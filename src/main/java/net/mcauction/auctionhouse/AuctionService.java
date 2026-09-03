package net.mcauction.auctionhouse;

import net.mcauction.auctionhouse.economy.EconomyService;
import net.mcauction.auctionhouse.event.AuctionListedEvent;
import net.mcauction.auctionhouse.model.Listing;
import net.mcauction.auctionhouse.model.ListingSort;
import net.mcauction.auctionhouse.model.ListingStatus;
import net.mcauction.auctionhouse.model.Notification;
import net.mcauction.auctionhouse.model.VaultItem;
import net.mcauction.auctionhouse.session.SellSession;
import net.mcauction.auctionhouse.storage.BidRepository;
import net.mcauction.auctionhouse.storage.ListingRepository;
import net.mcauction.auctionhouse.storage.NotificationRepository;
import net.mcauction.auctionhouse.storage.VaultRepository;
import net.mcauction.auctionhouse.util.ItemSerialization;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * オークションの出品・入札・落札・保管庫・通知に関する業務ロジックをまとめたサービス層。
 * 入札・落札・取消は出品単位のロックで経済操作とDB更新をアトミックに扱う。
 * 経済API(Vault)の呼び出しは、このプラグインではコマンド/GUIクリック/スケジューラのいずれも
 * メインスレッド上でのみ実行されるため、常にメインスレッドから呼ばれる。
 */
public class AuctionService {

    public enum SellStartResult {
        OK, EMPTY_HAND, BANNED_MATERIAL, LIMIT_REACHED
    }

    public enum SellConfirmResult {
        OK, ITEM_CHANGED, INSUFFICIENT_FUNDS, FAILED
    }

    public enum BidResult {
        SUCCESS, BOUGHT_OUT, INVALID_TOO_LOW, INSUFFICIENT_FUNDS, OWN_LISTING, NOT_ACTIVE, FAILED
    }

    public enum BuyoutResult {
        SUCCESS, NOT_AVAILABLE, OWN_LISTING, NOT_ACTIVE, INSUFFICIENT_FUNDS, FAILED
    }

    public enum CancelResult {
        SUCCESS, HAS_BIDS, NOT_OWNER, NOT_ACTIVE, NOT_FOUND
    }

    private final ListingRepository listingRepository;
    private final BidRepository bidRepository;
    private final VaultRepository vaultRepository;
    private final NotificationRepository notificationRepository;
    private final EconomyService economyService;
    private final MessageUtil messages;
    private final FileConfiguration config;
    private final Logger logger;
    private final Map<Integer, Object> listingLocks = new ConcurrentHashMap<>();

    public AuctionService(ListingRepository listingRepository, BidRepository bidRepository,
                           VaultRepository vaultRepository, NotificationRepository notificationRepository,
                           EconomyService economyService, MessageUtil messages, FileConfiguration config,
                           Logger logger) {
        this.listingRepository = listingRepository;
        this.bidRepository = bidRepository;
        this.vaultRepository = vaultRepository;
        this.notificationRepository = notificationRepository;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;
        this.logger = logger;
    }

    private Object lockFor(int listingId) {
        return listingLocks.computeIfAbsent(listingId, id -> new Object());
    }

    // ---------------------------------------------------------------
    // 起動時検証・定期処理
    // ---------------------------------------------------------------

    public void validateListingsOnStartup() {
        List<Listing> active;
        try {
            active = listingRepository.findActive(ListingSort.NEWEST);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "出品データの起動時検証に失敗しました", e);
            return;
        }
        long now = System.currentTimeMillis();
        for (Listing listing : active) {
            Optional<ItemStack> item = ItemSerialization.deserialize(listing.getItemData(), logger,
                    "listings#" + listing.getId());
            if (item.isEmpty()) {
                logger.severe("出品ID " + listing.getId() + " のアイテムデータが壊れているため、安全側で取り消し扱いにします。");
                try {
                    listingRepository.markCancelled(listing.getId(), now);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "破損出品の取り消しに失敗しました。出品ID: " + listing.getId(), e);
                }
            }
        }
    }

    public void processEndedAuctions() {
        long now = System.currentTimeMillis();
        List<Listing> ending;
        try {
            ending = listingRepository.findActiveEndingBefore(now);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "終了対象出品の取得に失敗しました", e);
            return;
        }
        for (Listing listing : ending) {
            synchronized (lockFor(listing.getId())) {
                Listing fresh = findOrNull(listing.getId());
                if (fresh == null || fresh.getStatus() != ListingStatus.ACTIVE || fresh.getEndAt() > now) {
                    // 既に即決購入・延長など他の経路で状態が変わっている
                    continue;
                }
                if (fresh.getBidCount() > 0 && fresh.getTopBidderUuid() != null) {
                    int saleFee = calculateSaleFee(fresh.getCurrentPrice());
                    boolean updated;
                    try {
                        updated = listingRepository.markSold(fresh.getId(), now, saleFee);
                    } catch (SQLException e) {
                        logger.log(Level.WARNING, "出品の落札確定に失敗しました。出品ID: " + fresh.getId(), e);
                        continue;
                    }
                    if (!updated) {
                        continue;
                    }
                    settleSaleSideEffects(fresh, fresh.getTopBidderUuid(), fresh.getTopBidderName(),
                            fresh.getCurrentPrice(), saleFee);
                } else {
                    boolean updated;
                    try {
                        updated = listingRepository.markExpired(fresh.getId(), now);
                    } catch (SQLException e) {
                        logger.log(Level.WARNING, "出品の失効処理に失敗しました。出品ID: " + fresh.getId(), e);
                        continue;
                    }
                    if (!updated) {
                        continue;
                    }
                    returnItemToSeller(fresh, "RETURNED");
                    Map<String, String> placeholders = Map.of("item", fresh.getDisplayName());
                    notifyPlayer(fresh.getSellerUuid(), messages.formatted("notify.unsold", placeholders));
                }
            }
        }
    }

    public void purgeOldListings() {
        int retentionDays = config.getInt("auction.retention-days", 30);
        if (retentionDays <= 0) {
            return;
        }
        long closedBefore = System.currentTimeMillis() - retentionDays * 86_400_000L;
        try {
            int deleted = listingRepository.deleteClosedBefore(closedBefore);
            if (deleted > 0) {
                logger.info("保持期間(" + retentionDays + "日)を過ぎた出品履歴を" + deleted + "件削除しました。");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "古い出品履歴の削除に失敗しました", e);
        }
    }

    // ---------------------------------------------------------------
    // 出品フロー
    // ---------------------------------------------------------------

    public SellStartResult canStartSell(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return SellStartResult.EMPTY_HAND;
        }
        List<String> banned = config.getStringList("auction.banned-materials");
        if (banned.contains(hand.getType().name())) {
            return SellStartResult.BANNED_MATERIAL;
        }
        int max = config.getInt("auction.max-listings-per-player", 3);
        try {
            if (listingRepository.countActiveBySeller(player.getUniqueId()) >= max) {
                return SellStartResult.LIMIT_REACHED;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "出品数の取得に失敗しました", e);
            return SellStartResult.LIMIT_REACHED;
        }
        return SellStartResult.OK;
    }

    public SellConfirmResult confirmSell(Player player, SellSession session) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.isSimilar(session.getSnapshotItem()) || hand.getAmount() < session.getAmount()) {
            return SellConfirmResult.ITEM_CHANGED;
        }

        int fee = calculateListingFee(session.getStartPrice());
        if (!economyService.has(player, fee)) {
            return SellConfirmResult.INSUFFICIENT_FUNDS;
        }

        ItemStack listingItem = session.getSnapshotItem().clone();
        listingItem.setAmount(session.getAmount());
        String itemData = ItemSerialization.serialize(listingItem);
        String displayName = resolveDisplayName(listingItem);

        long now = System.currentTimeMillis();
        long endAt = now + session.getDurationHours() * 3_600_000L;

        if (!economyService.withdraw(player, fee)) {
            return SellConfirmResult.INSUFFICIENT_FUNDS;
        }

        Listing listing;
        try {
            listing = listingRepository.insert(player.getUniqueId(), player.getName(), itemData, displayName,
                    session.getAmount(), session.getStartPrice(), session.getBuyoutPrice(), now, endAt, fee);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "出品の保存に失敗しました", e);
            economyService.deposit(player, fee);
            return SellConfirmResult.FAILED;
        }

        if (hand.getAmount() == session.getAmount()) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            ItemStack remaining = hand.clone();
            remaining.setAmount(hand.getAmount() - session.getAmount());
            player.getInventory().setItemInMainHand(remaining);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", displayName);
        placeholders.put("amount", String.valueOf(session.getAmount()));
        placeholders.put("price", economyService.format(session.getStartPrice()));
        placeholders.put("fee", economyService.format(fee));
        messages.send(player, "sell.completed", placeholders);

        Map<String, String> announcePlaceholders = new HashMap<>();
        announcePlaceholders.put("player", player.getName());
        announcePlaceholders.put("item", displayName);
        announcePlaceholders.put("price", economyService.format(session.getStartPrice()));
        Bukkit.broadcastMessage(messages.formatted("announce.listed", announcePlaceholders));

        // 外部プラグイン向けの出品成立通知イベント。ここまで来た時点で保存・徴収・回収は完了している
        Bukkit.getPluginManager().callEvent(new AuctionListedEvent(listing.getId(), player.getUniqueId(),
                player.getName(), displayName, session.getAmount(), session.getStartPrice(), session.getBuyoutPrice(),
                session.getDurationHours(), economyService.format(session.getStartPrice()),
                economyService.format(session.getBuyoutPrice())));

        logger.fine("出品ID " + listing.getId() + " を作成しました。");
        return SellConfirmResult.OK;
    }

    // ---------------------------------------------------------------
    // 入札・即決購入
    // ---------------------------------------------------------------

    public BidResult placeBid(Player player, int listingId, int amount) {
        synchronized (lockFor(listingId)) {
            Listing listing = findOrNull(listingId);
            if (listing == null || listing.getStatus() != ListingStatus.ACTIVE) {
                return BidResult.NOT_ACTIVE;
            }
            if (listing.getSellerUuid().equals(player.getUniqueId())) {
                return BidResult.OWN_LISTING;
            }
            if (listing.hasBuyout() && amount >= listing.getBuyoutPrice()) {
                BuyoutResult result = executeBuyout(player, listing);
                return switch (result) {
                    case SUCCESS -> BidResult.BOUGHT_OUT;
                    case INSUFFICIENT_FUNDS -> BidResult.INSUFFICIENT_FUNDS;
                    default -> BidResult.FAILED;
                };
            }

            int minBid = calculateMinBid(listing);
            if (amount < minBid) {
                return BidResult.INVALID_TOO_LOW;
            }
            if (!economyService.has(player, amount)) {
                return BidResult.INSUFFICIENT_FUNDS;
            }
            if (!economyService.withdraw(player, amount)) {
                return BidResult.INSUFFICIENT_FUNDS;
            }

            UUID previousBidder = listing.getTopBidderUuid();
            String previousBidderName = listing.getTopBidderName();
            int previousAmount = listing.getCurrentPrice();
            boolean hadPreviousBid = listing.getBidCount() > 0;

            long now = System.currentTimeMillis();
            long endAt = listing.getEndAt();
            int extensionCount = listing.getExtensionCount();
            int thresholdSeconds = config.getInt("auction.extension-threshold-seconds", 180);
            int extensionSeconds = config.getInt("auction.extension-seconds", 180);
            int maxExtensions = config.getInt("auction.max-extensions", 10);
            boolean extended = false;
            if (endAt - now <= thresholdSeconds * 1000L && extensionCount < maxExtensions) {
                endAt = now + extensionSeconds * 1000L;
                extensionCount++;
                extended = true;
            }

            try {
                listingRepository.updateAfterBid(listingId, amount, listing.getBidCount() + 1,
                        player.getUniqueId(), player.getName(), endAt, extensionCount);
                bidRepository.insert(listingId, player.getUniqueId(), player.getName(), amount, now);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "入札の保存に失敗しました。出品ID: " + listingId, e);
                economyService.deposit(player, amount);
                return BidResult.FAILED;
            }

            if (hadPreviousBid && previousBidder != null) {
                refundAndNotifyOutbid(previousBidder, previousBidderName, previousAmount, listing, amount);
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", listing.getDisplayName());
            placeholders.put("price", economyService.format(amount));
            messages.send(player, "bid.success", placeholders);

            if (extended) {
                announceExtension(listing);
            }

            return BidResult.SUCCESS;
        }
    }

    public BuyoutResult buyout(Player player, int listingId) {
        synchronized (lockFor(listingId)) {
            Listing listing = findOrNull(listingId);
            if (listing == null || listing.getStatus() != ListingStatus.ACTIVE) {
                return BuyoutResult.NOT_ACTIVE;
            }
            if (!listing.hasBuyout()) {
                return BuyoutResult.NOT_AVAILABLE;
            }
            if (listing.getSellerUuid().equals(player.getUniqueId())) {
                return BuyoutResult.OWN_LISTING;
            }
            return executeBuyout(player, listing);
        }
    }

    private BuyoutResult executeBuyout(Player player, Listing listing) {
        int price = listing.getBuyoutPrice();
        if (!economyService.has(player, price)) {
            return BuyoutResult.INSUFFICIENT_FUNDS;
        }
        if (!economyService.withdraw(player, price)) {
            return BuyoutResult.INSUFFICIENT_FUNDS;
        }

        UUID previousBidder = listing.getTopBidderUuid();
        String previousBidderName = listing.getTopBidderName();
        int previousAmount = listing.getCurrentPrice();
        boolean hadPreviousBid = listing.getBidCount() > 0;

        long now = System.currentTimeMillis();
        int saleFee = calculateSaleFee(price);

        try {
            listingRepository.updateAfterBid(listing.getId(), price, listing.getBidCount() + 1,
                    player.getUniqueId(), player.getName(), listing.getEndAt(), listing.getExtensionCount());
            bidRepository.insert(listing.getId(), player.getUniqueId(), player.getName(), price, now);
            listingRepository.markSold(listing.getId(), now, saleFee);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "即決購入の保存に失敗しました。出品ID: " + listing.getId(), e);
            economyService.deposit(player, price);
            return BuyoutResult.FAILED;
        }

        if (hadPreviousBid && previousBidder != null) {
            refundAndNotifyOutbid(previousBidder, previousBidderName, previousAmount, listing, price);
        }

        settleSaleSideEffects(listing, player.getUniqueId(), player.getName(), price, saleFee);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", listing.getDisplayName());
        placeholders.put("price", economyService.format(price));
        messages.send(player, "buyout.success", placeholders);

        return BuyoutResult.SUCCESS;
    }

    // ---------------------------------------------------------------
    // 出品の取り消し
    // ---------------------------------------------------------------

    public CancelResult cancelListing(Player player, int listingId) {
        synchronized (lockFor(listingId)) {
            Listing listing = findOrNull(listingId);
            if (listing == null) {
                return CancelResult.NOT_FOUND;
            }
            if (!listing.getSellerUuid().equals(player.getUniqueId())) {
                return CancelResult.NOT_OWNER;
            }
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                return CancelResult.NOT_ACTIVE;
            }
            if (listing.getBidCount() > 0) {
                return CancelResult.HAS_BIDS;
            }

            long now = System.currentTimeMillis();
            boolean updated;
            try {
                updated = listingRepository.markCancelled(listingId, now);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "出品の取り消しに失敗しました。出品ID: " + listingId, e);
                return CancelResult.NOT_ACTIVE;
            }
            if (!updated) {
                return CancelResult.NOT_ACTIVE;
            }
            returnItemToSeller(listing, "CANCELLED");
            Map<String, String> placeholders = Map.of("item", listing.getDisplayName());
            messages.send(player, "cancel-listing.success", placeholders);
            return CancelResult.SUCCESS;
        }
    }

    public CancelResult adminCancel(Player admin, int listingId) {
        synchronized (lockFor(listingId)) {
            Listing listing = findOrNull(listingId);
            if (listing == null) {
                return CancelResult.NOT_FOUND;
            }
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                return CancelResult.NOT_ACTIVE;
            }

            long now = System.currentTimeMillis();
            boolean updated;
            try {
                updated = listingRepository.markCancelled(listingId, now);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "出品の強制取り消しに失敗しました。出品ID: " + listingId, e);
                return CancelResult.NOT_ACTIVE;
            }
            if (!updated) {
                return CancelResult.NOT_ACTIVE;
            }

            returnItemToSeller(listing, "CANCELLED");

            if (listing.getBidCount() > 0 && listing.getTopBidderUuid() != null) {
                economyService.deposit(Bukkit.getOfflinePlayer(listing.getTopBidderUuid()), listing.getCurrentPrice());
                Map<String, String> bidderPlaceholders = new HashMap<>();
                bidderPlaceholders.put("item", listing.getDisplayName());
                bidderPlaceholders.put("amount", economyService.format(listing.getCurrentPrice()));
                notifyPlayer(listing.getTopBidderUuid(), messages.formatted("notify.bid-refunded-by-admin", bidderPlaceholders));
            }

            Map<String, String> sellerPlaceholders = Map.of("item", listing.getDisplayName());
            notifyPlayer(listing.getSellerUuid(), messages.formatted("notify.listing-cancelled-by-admin", sellerPlaceholders));

            Map<String, String> adminPlaceholders = new HashMap<>();
            adminPlaceholders.put("id", String.valueOf(listingId));
            adminPlaceholders.put("item", listing.getDisplayName());
            messages.send(admin, "admin.cancelled", adminPlaceholders);
            return CancelResult.SUCCESS;
        }
    }

    // ---------------------------------------------------------------
    // 保管庫
    // ---------------------------------------------------------------

    public void receiveVaultItem(Player player, int vaultItemId) {
        List<VaultItem> items;
        try {
            items = vaultRepository.findByOwner(player.getUniqueId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫の取得に失敗しました", e);
            return;
        }
        VaultItem target = items.stream().filter(v -> v.getId() == vaultItemId).findFirst().orElse(null);
        if (target == null) {
            return;
        }

        Optional<ItemStack> deserialized = ItemSerialization.deserialize(target.getItemData(), logger,
                "vault_items#" + target.getId());
        if (deserialized.isEmpty()) {
            try {
                vaultRepository.deleteById(target.getId());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "破損した保管庫アイテムの削除に失敗しました", e);
            }
            return;
        }

        ItemStack item = deserialized.get();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (leftover.isEmpty()) {
            try {
                vaultRepository.deleteById(target.getId());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "保管庫アイテムの削除に失敗しました", e);
            }
            Map<String, String> placeholders = Map.of("item", resolveDisplayName(item));
            messages.send(player, "vault.received", placeholders);
        } else {
            ItemStack remaining = leftover.values().iterator().next();
            try {
                vaultRepository.updateRemainder(target.getId(), ItemSerialization.serialize(remaining),
                        remaining.getAmount());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "保管庫アイテムの更新に失敗しました", e);
            }
            messages.send(player, "vault.full");
        }
    }

    // ---------------------------------------------------------------
    // 通知
    // ---------------------------------------------------------------

    public void deliverQueuedNotifications(Player player) {
        List<Notification> pending;
        try {
            pending = notificationRepository.findByOwner(player.getUniqueId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "通知の取得に失敗しました", e);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        for (Notification notification : pending) {
            player.sendMessage(notification.getMessage());
        }
        try {
            notificationRepository.deleteByOwner(player.getUniqueId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "通知の削除に失敗しました", e);
        }
    }

    private void notifyPlayer(UUID uuid, String formattedMessage) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(formattedMessage);
            return;
        }
        try {
            notificationRepository.insert(uuid, formattedMessage, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "通知の保存に失敗しました", e);
        }
    }

    // ---------------------------------------------------------------
    // 参照系
    // ---------------------------------------------------------------

    public Listing findListing(int id) {
        return findOrNull(id);
    }

    public List<Listing> findActiveListings(ListingSort sort) {
        try {
            return listingRepository.findActive(sort);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "出品一覧の取得に失敗しました", e);
            return List.of();
        }
    }

    public List<Listing> findMyListings(UUID uuid) {
        try {
            return listingRepository.findBySeller(uuid);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "自分の出品の取得に失敗しました", e);
            return List.of();
        }
    }

    public List<Listing> findMyBids(UUID uuid) {
        try {
            return listingRepository.findByBidder(uuid);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "入札中の出品の取得に失敗しました", e);
            return List.of();
        }
    }

    public List<VaultItem> findVaultItems(UUID uuid) {
        try {
            return vaultRepository.findByOwner(uuid);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫の取得に失敗しました", e);
            return List.of();
        }
    }

    // ---------------------------------------------------------------
    // 計算ロジック
    // ---------------------------------------------------------------

    public int calculateListingFee(int startPrice) {
        double rate = config.getDouble("auction.listing-fee-rate", 0.05);
        int min = config.getInt("auction.listing-fee-min", 100);
        int fee = (int) Math.ceil(startPrice * rate);
        return Math.max(fee, min);
    }

    public int calculateSaleFee(int price) {
        double rate = config.getDouble("auction.sale-fee-rate", 0.10);
        return (int) Math.ceil(price * rate);
    }

    public int calculateMinBid(Listing listing) {
        if (listing.getBidCount() == 0) {
            return listing.getStartPrice();
        }
        double incRate = config.getDouble("auction.min-increment-rate", 0.05);
        int incFlat = config.getInt("auction.min-increment-flat", 100);
        int increment = Math.max((int) Math.ceil(listing.getCurrentPrice() * incRate), incFlat);
        return listing.getCurrentPrice() + increment;
    }

    public String resolveDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return humanize(item.getType());
    }

    // ---------------------------------------------------------------
    // 内部ヘルパー
    // ---------------------------------------------------------------

    private void settleSaleSideEffects(Listing listing, UUID winnerUuid, String winnerName, int finalPrice,
                                        int saleFee) {
        try {
            vaultRepository.insert(winnerUuid, winnerName, listing.getItemData(), listing.getAmount(), "WON",
                    System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "落札アイテムの保管庫登録に失敗しました。出品ID: " + listing.getId(), e);
        }

        int payout = finalPrice - saleFee;
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.getSellerUuid());
        boolean paid = economyService.deposit(seller, payout);
        if (!paid) {
            logger.warning("出品ID " + listing.getId() + " の売上支払いに失敗しました。管理者による手動対応が必要です。");
        }

        Map<String, String> wonPlaceholders = Map.of(
                "item", listing.getDisplayName(), "price", economyService.format(finalPrice));
        notifyPlayer(winnerUuid, messages.formatted("notify.won", wonPlaceholders));

        Map<String, String> soldPlaceholders = new HashMap<>();
        soldPlaceholders.put("item", listing.getDisplayName());
        soldPlaceholders.put("price", economyService.format(finalPrice));
        soldPlaceholders.put("fee", economyService.format(saleFee));
        soldPlaceholders.put("amount", economyService.format(payout));
        notifyPlayer(listing.getSellerUuid(), messages.formatted("notify.sold", soldPlaceholders));

        Map<String, String> announcePlaceholders = new HashMap<>();
        announcePlaceholders.put("item", listing.getDisplayName());
        announcePlaceholders.put("buyer", winnerName);
        announcePlaceholders.put("price", economyService.format(finalPrice));
        Bukkit.broadcastMessage(messages.formatted("announce.sold", announcePlaceholders));
    }

    private void refundAndNotifyOutbid(UUID previousBidder, String previousBidderName, int previousAmount,
                                        Listing listing, int newPrice) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(previousBidder);
        boolean refunded = economyService.deposit(offline, previousAmount);
        if (!refunded) {
            logger.warning("出品ID " + listing.getId() + " の入札返金に失敗しました。対象: " + previousBidderName);
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", listing.getDisplayName());
        placeholders.put("price", economyService.format(newPrice));
        notifyPlayer(previousBidder, messages.formatted("notify.outbid", placeholders));
    }

    private void announceExtension(Listing listing) {
        Map<String, String> placeholders = Map.of("item", listing.getDisplayName());
        String formatted = messages.formatted("bid.extended", placeholders);
        Bukkit.broadcastMessage(formatted);
        notifyPlayer(listing.getSellerUuid(), formatted);
    }

    private void returnItemToSeller(Listing listing, String reason) {
        try {
            vaultRepository.insert(listing.getSellerUuid(), listing.getSellerName(), listing.getItemData(),
                    listing.getAmount(), reason, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "アイテムの保管庫返却に失敗しました。出品ID: " + listing.getId(), e);
        }
    }

    private Listing findOrNull(int id) {
        try {
            return listingRepository.findById(id);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "出品の取得に失敗しました。出品ID: " + id, e);
            return null;
        }
    }

    private String humanize(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
