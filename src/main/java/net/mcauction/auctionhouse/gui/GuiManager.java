package net.mcauction.auctionhouse.gui;

import net.mcauction.auctionhouse.AuctionService;
import net.mcauction.auctionhouse.economy.EconomyService;
import net.mcauction.auctionhouse.model.Listing;
import net.mcauction.auctionhouse.model.ListingSort;
import net.mcauction.auctionhouse.model.ListingStatus;
import net.mcauction.auctionhouse.model.VaultItem;
import net.mcauction.auctionhouse.util.ItemBuilder;
import net.mcauction.auctionhouse.util.ItemSerialization;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class GuiManager {

    private static final int PAGE_SIZE = 45;

    private final AuctionService auctionService;
    private final EconomyService economyService;
    private final MessageUtil messages;
    private final Logger logger;

    public GuiManager(AuctionService auctionService, EconomyService economyService, MessageUtil messages,
                       Logger logger) {
        this.auctionService = auctionService;
        this.economyService = economyService;
        this.messages = messages;
        this.logger = logger;
    }

    public void openMainMenu(Player player) {
        MainMenuHolder holder = new MainMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.main-title"));
        holder.setInventory(inventory);

        inventory.setItem(MainMenuHolder.SLOT_BROWSE, new ItemBuilder(Material.WRITTEN_BOOK)
                .name(messages.get("main-menu.browse"))
                .lore(messages.get("main-menu.browse-lore"))
                .build());
        inventory.setItem(MainMenuHolder.SLOT_SELL, new ItemBuilder(Material.GOLD_NUGGET)
                .name(messages.get("main-menu.sell"))
                .lore(messages.get("main-menu.sell-lore"))
                .build());
        inventory.setItem(MainMenuHolder.SLOT_MY_LISTINGS, new ItemBuilder(Material.NAME_TAG)
                .name(messages.get("main-menu.my-listings"))
                .lore(messages.get("main-menu.my-listings-lore"))
                .build());
        inventory.setItem(MainMenuHolder.SLOT_MY_BIDS, new ItemBuilder(Material.GOLD_INGOT)
                .name(messages.get("main-menu.my-bids"))
                .lore(messages.get("main-menu.my-bids-lore"))
                .build());
        inventory.setItem(MainMenuHolder.SLOT_VAULT, new ItemBuilder(Material.CHEST)
                .name(messages.get("main-menu.vault"))
                .lore(messages.get("main-menu.vault-lore"))
                .build());

        player.openInventory(inventory);
    }

    public void openListingList(Player player, int page, ListingSort sort) {
        List<Listing> active = auctionService.findActiveListings(sort);
        int totalPages = Math.max(1, (int) Math.ceil(active.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        ListingListHolder holder = new ListingListHolder();
        holder.setPage(page);
        holder.setSort(sort);
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.list-title"));
        holder.setInventory(inventory);

        int from = page * PAGE_SIZE;
        int to = Math.min(active.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Listing listing = active.get(i);
            int slot = i - from;
            inventory.setItem(slot, buildListingItem(listing));
            holder.getSlotToListingId().put(slot, listing.getId());
        }

        if (active.isEmpty()) {
            player.sendMessage(messages.get("prefix") + messages.get("list.empty"));
        }

        if (page > 0) {
            inventory.setItem(ListingListHolder.SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name("§a前のページ").build());
        }
        inventory.setItem(ListingListHolder.SLOT_SORT, new ItemBuilder(Material.HOPPER)
                .name(messages.get("list.sort-label", Map.of("mode", sortLabel(sort))))
                .lore("§7クリックで並び替えを切り替え")
                .build());
        inventory.setItem(ListingListHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());
        if (page < totalPages - 1) {
            inventory.setItem(ListingListHolder.SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name("§a次のページ").build());
        }

        player.openInventory(inventory);
    }

    public void openListingDetail(Player player, int listingId) {
        Listing listing = auctionService.findListing(listingId);
        if (listing == null) {
            player.closeInventory();
            return;
        }

        ListingDetailHolder holder = new ListingDetailHolder(listingId);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.detail-title"));
        holder.setInventory(inventory);

        ItemStack display = deserializeOrBarrier(listing.getItemData(), listing.getId());
        List<String> lore = new ArrayList<>();
        lore.add("§7出品者: §f" + listing.getSellerName());
        lore.add("§7個数: §f" + listing.getAmount());
        lore.add("§7状態: §f" + statusLabel(listing.getStatus()));
        lore.add("§7開始価格: §f" + economyService.format(listing.getStartPrice()));
        lore.add("§7現在価格: §e" + economyService.format(listing.getCurrentPrice()));
        if (listing.hasBuyout()) {
            lore.add("§7即決価格: §e" + economyService.format(listing.getBuyoutPrice()));
        }
        lore.add("§7入札数: §f" + listing.getBidCount());
        if (listing.getTopBidderName() != null) {
            lore.add("§7最高入札者: §f" + listing.getTopBidderName());
        }
        if (listing.getStatus() == ListingStatus.ACTIVE) {
            lore.add("§7残り時間: §f" + formatRemaining(listing.getEndAt()));
        }
        inventory.setItem(13, new ItemBuilder(display).lore(lore).build());

        boolean isOwn = listing.getSellerUuid().equals(player.getUniqueId());
        boolean active = listing.getStatus() == ListingStatus.ACTIVE;

        if (active && !isOwn) {
            inventory.setItem(ListingDetailHolder.SLOT_BID, new ItemBuilder(Material.LIME_WOOL)
                    .name("§a入札する").build());
            if (listing.hasBuyout()) {
                inventory.setItem(ListingDetailHolder.SLOT_BUYOUT, new ItemBuilder(Material.GOLD_INGOT)
                        .name("§6即決で買う (" + economyService.format(listing.getBuyoutPrice()) + ")").build());
            }
        }
        if (active && isOwn && listing.getBidCount() == 0) {
            inventory.setItem(ListingDetailHolder.SLOT_CANCEL, new ItemBuilder(Material.RED_WOOL)
                    .name("§c出品を取り消す").build());
        }
        inventory.setItem(ListingDetailHolder.SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("§7戻る(メニューへ)").build());

        player.openInventory(inventory);
    }

    public void openBuyoutConfirm(Player player, int listingId) {
        Listing listing = auctionService.findListing(listingId);
        if (listing == null || listing.getStatus() != ListingStatus.ACTIVE || !listing.hasBuyout()) {
            player.closeInventory();
            return;
        }

        BuyoutConfirmHolder holder = new BuyoutConfirmHolder(listingId);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.buyout-confirm-title"));
        holder.setInventory(inventory);

        ItemStack display = deserializeOrBarrier(listing.getItemData(), listing.getId());
        List<String> lore = List.of(
                "§7即決価格: §e" + economyService.format(listing.getBuyoutPrice()),
                "",
                "§7この価格で今すぐ購入します。");
        inventory.setItem(13, new ItemBuilder(display).lore(lore).build());

        inventory.setItem(BuyoutConfirmHolder.SLOT_CONFIRM, new ItemBuilder(Material.LIME_WOOL)
                .name("§aはい、購入する").build());
        inventory.setItem(BuyoutConfirmHolder.SLOT_CANCEL, new ItemBuilder(Material.RED_WOOL)
                .name("§cいいえ").build());

        player.openInventory(inventory);
    }

    public void openMyListings(Player player) {
        List<Listing> mine = auctionService.findMyListings(player.getUniqueId());
        MyListingsHolder holder = new MyListingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.my-listings-title"));
        holder.setInventory(inventory);

        int slot = 0;
        for (Listing listing : mine) {
            if (slot >= PAGE_SIZE) {
                break;
            }
            ItemStack display = deserializeOrBarrier(listing.getItemData(), listing.getId());
            boolean cancellable = listing.getStatus() == ListingStatus.ACTIVE && listing.getBidCount() == 0;

            List<String> lore = new ArrayList<>();
            lore.add("§7状態: §f" + statusLabel(listing.getStatus()));
            lore.add("§7現在価格: §e" + economyService.format(listing.getCurrentPrice()));
            lore.add("§7入札数: §f" + listing.getBidCount());
            lore.add("");
            lore.add(cancellable ? "§cクリックで取り消す" : "§7クリックで詳細を見る");

            inventory.setItem(slot, new ItemBuilder(display).lore(lore).build());
            holder.getSlotToListingId().put(slot, listing.getId());
            holder.getSlotCancellable().put(slot, cancellable);
            slot++;
        }

        if (mine.isEmpty()) {
            player.sendMessage(messages.get("prefix") + "§e出品したことはありません。");
        }
        inventory.setItem(MyListingsHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());

        player.openInventory(inventory);
    }

    public void openMyBids(Player player) {
        List<Listing> mine = auctionService.findMyBids(player.getUniqueId());
        MyBidsHolder holder = new MyBidsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.my-bids-title"));
        holder.setInventory(inventory);

        int slot = 0;
        for (Listing listing : mine) {
            if (slot >= PAGE_SIZE) {
                break;
            }
            ItemStack display = deserializeOrBarrier(listing.getItemData(), listing.getId());
            boolean winning = player.getUniqueId().equals(listing.getTopBidderUuid());

            List<String> lore = new ArrayList<>();
            lore.add("§7状態: §f" + statusLabel(listing.getStatus()));
            lore.add("§7現在価格: §e" + economyService.format(listing.getCurrentPrice()));
            if (listing.getStatus() == ListingStatus.ACTIVE) {
                lore.add(winning ? "§a現在、最高額入札者です" : "§c他のプレイヤーに上回られています");
            } else {
                lore.add(winning ? "§a落札しました(保管庫を確認)" : "§7落札できませんでした");
            }
            lore.add("");
            lore.add("§eクリックで詳細を見る");

            inventory.setItem(slot, new ItemBuilder(display).lore(lore).build());
            holder.getSlotToListingId().put(slot, listing.getId());
            slot++;
        }

        if (mine.isEmpty()) {
            player.sendMessage(messages.get("prefix") + "§e入札中の出品はありません。");
        }
        inventory.setItem(MyBidsHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());

        player.openInventory(inventory);
    }

    public void openVault(Player player, int page) {
        List<VaultItem> items = auctionService.findVaultItems(player.getUniqueId());
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        VaultHolder holder = new VaultHolder();
        holder.setPage(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.vault-title"));
        holder.setInventory(inventory);

        int from = page * PAGE_SIZE;
        int to = Math.min(items.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            VaultItem item = items.get(i);
            int slot = i - from;
            ItemStack display = deserializeVaultOrBarrier(item);
            inventory.setItem(slot, new ItemBuilder(display).lore("§eクリックで受け取る").build());
            holder.getSlotToVaultId().put(slot, item.getId());
        }

        if (items.isEmpty()) {
            player.sendMessage(messages.get("prefix") + messages.get("vault.empty"));
        }
        if (page > 0) {
            inventory.setItem(VaultHolder.SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name("§a前のページ").build());
        }
        inventory.setItem(VaultHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());
        if (page < totalPages - 1) {
            inventory.setItem(VaultHolder.SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name("§a次のページ").build());
        }

        player.openInventory(inventory);
    }

    private ItemStack buildListingItem(Listing listing) {
        ItemStack display = deserializeOrBarrier(listing.getItemData(), listing.getId());

        List<String> lore = new ArrayList<>();
        lore.add("§7出品者: §f" + listing.getSellerName());
        lore.add("§7個数: §f" + listing.getAmount());
        lore.add("§7現在価格: §e" + economyService.format(listing.getCurrentPrice()));
        if (listing.hasBuyout()) {
            lore.add("§7即決価格: §e" + economyService.format(listing.getBuyoutPrice()));
        }
        lore.add("§7入札数: §f" + listing.getBidCount());
        lore.add("§7残り時間: §f" + formatRemaining(listing.getEndAt()));
        lore.add("");
        lore.add("§eクリックで詳細を見る");

        return new ItemBuilder(display).lore(lore).build();
    }

    private ItemStack deserializeOrBarrier(String itemData, int listingId) {
        Optional<ItemStack> deserialized = ItemSerialization.deserialize(itemData, logger, "listings#" + listingId);
        return deserialized.orElseGet(() -> new ItemBuilder(Material.BARRIER)
                .name("§c(データを復元できませんでした)").build());
    }

    private ItemStack deserializeVaultOrBarrier(VaultItem item) {
        Optional<ItemStack> deserialized = ItemSerialization.deserialize(item.getItemData(), logger,
                "vault_items#" + item.getId());
        return deserialized.orElseGet(() -> new ItemBuilder(Material.BARRIER)
                .name("§c(データを復元できませんでした)").build());
    }

    private String statusLabel(ListingStatus status) {
        return switch (status) {
            case ACTIVE -> "§a出品中";
            case SOLD -> "§b落札済み";
            case EXPIRED -> "§7売れ残り";
            case CANCELLED -> "§7取消済み";
        };
    }

    private String sortLabel(ListingSort sort) {
        return switch (sort) {
            case ENDING_SOON -> messages.get("list.sort-ending-soon");
            case NEWEST -> messages.get("list.sort-newest");
            case PRICE_LOW -> messages.get("list.sort-price-low");
            case PRICE_HIGH -> messages.get("list.sort-price-high");
        };
    }

    private String formatRemaining(long endAt) {
        long remaining = endAt - System.currentTimeMillis();
        if (remaining <= 0) {
            return "まもなく終了";
        }
        long totalMinutes = remaining / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "時間" + minutes + "分";
        }
        return minutes + "分";
    }
}
