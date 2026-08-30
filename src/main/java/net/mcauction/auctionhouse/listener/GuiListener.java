package net.mcauction.auctionhouse.listener;

import net.mcauction.auctionhouse.AuctionService;
import net.mcauction.auctionhouse.gui.BuyoutConfirmHolder;
import net.mcauction.auctionhouse.gui.GuiManager;
import net.mcauction.auctionhouse.gui.ListingDetailHolder;
import net.mcauction.auctionhouse.gui.ListingListHolder;
import net.mcauction.auctionhouse.gui.MainMenuHolder;
import net.mcauction.auctionhouse.gui.MyBidsHolder;
import net.mcauction.auctionhouse.gui.MyListingsHolder;
import net.mcauction.auctionhouse.gui.VaultHolder;
import net.mcauction.auctionhouse.model.ListingSort;
import net.mcauction.auctionhouse.session.BidConversation;
import net.mcauction.auctionhouse.session.SellConversation;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public class GuiListener implements Listener {

    private final GuiManager guiManager;
    private final AuctionService auctionService;
    private final SellConversation sellConversation;
    private final BidConversation bidConversation;
    private final MessageUtil messages;
    private final Plugin plugin;

    public GuiListener(GuiManager guiManager, AuctionService auctionService, SellConversation sellConversation,
                        BidConversation bidConversation, MessageUtil messages, Plugin plugin) {
        this.guiManager = guiManager;
        this.auctionService = auctionService;
        this.sellConversation = sellConversation;
        this.bidConversation = bidConversation;
        this.messages = messages;
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MainMenuHolder) && !(holder instanceof ListingListHolder)
                && !(holder instanceof ListingDetailHolder) && !(holder instanceof BuyoutConfirmHolder)
                && !(holder instanceof MyListingsHolder) && !(holder instanceof MyBidsHolder)
                && !(holder instanceof VaultHolder)) {
            return;
        }
        // GUI内のアイテムは取り出し不可(全クリックをキャンセル)
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder instanceof MainMenuHolder) {
            handleMainMenu(player, slot);
        } else if (holder instanceof ListingListHolder listHolder) {
            handleListingList(player, listHolder, slot);
        } else if (holder instanceof ListingDetailHolder detailHolder) {
            handleListingDetail(player, detailHolder, slot);
        } else if (holder instanceof BuyoutConfirmHolder confirmHolder) {
            handleBuyoutConfirm(player, confirmHolder, slot);
        } else if (holder instanceof MyListingsHolder myListingsHolder) {
            handleMyListings(player, myListingsHolder, slot);
        } else if (holder instanceof MyBidsHolder myBidsHolder) {
            handleMyBids(player, myBidsHolder, slot);
        } else if (holder instanceof VaultHolder vaultHolder) {
            handleVault(player, vaultHolder, slot);
        }
    }

    private void handleMainMenu(Player player, int slot) {
        if (slot == MainMenuHolder.SLOT_BROWSE) {
            guiManager.openListingList(player, 0, ListingSort.ENDING_SOON);
        } else if (slot == MainMenuHolder.SLOT_SELL) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> sellConversation.start(player));
        } else if (slot == MainMenuHolder.SLOT_MY_LISTINGS) {
            guiManager.openMyListings(player);
        } else if (slot == MainMenuHolder.SLOT_MY_BIDS) {
            guiManager.openMyBids(player);
        } else if (slot == MainMenuHolder.SLOT_VAULT) {
            guiManager.openVault(player, 0);
        }
    }

    private void handleListingList(Player player, ListingListHolder holder, int slot) {
        if (slot == ListingListHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        if (slot == ListingListHolder.SLOT_PREV) {
            guiManager.openListingList(player, holder.getPage() - 1, holder.getSort());
            return;
        }
        if (slot == ListingListHolder.SLOT_NEXT) {
            guiManager.openListingList(player, holder.getPage() + 1, holder.getSort());
            return;
        }
        if (slot == ListingListHolder.SLOT_SORT) {
            guiManager.openListingList(player, 0, holder.getSort().next());
            return;
        }
        Integer listingId = holder.getSlotToListingId().get(slot);
        if (listingId != null) {
            guiManager.openListingDetail(player, listingId);
        }
    }

    private void handleListingDetail(Player player, ListingDetailHolder holder, int slot) {
        if (slot == ListingDetailHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        if (slot == ListingDetailHolder.SLOT_BID) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> bidConversation.start(player, holder.getListingId()));
            return;
        }
        if (slot == ListingDetailHolder.SLOT_BUYOUT) {
            guiManager.openBuyoutConfirm(player, holder.getListingId());
            return;
        }
        if (slot == ListingDetailHolder.SLOT_CANCEL) {
            AuctionService.CancelResult result = auctionService.cancelListing(player, holder.getListingId());
            handleCancelResult(player, result);
            guiManager.openMainMenu(player);
        }
    }

    private void handleBuyoutConfirm(Player player, BuyoutConfirmHolder holder, int slot) {
        if (slot == BuyoutConfirmHolder.SLOT_CANCEL) {
            guiManager.openListingDetail(player, holder.getListingId());
            return;
        }
        if (slot == BuyoutConfirmHolder.SLOT_CONFIRM) {
            AuctionService.BuyoutResult result = auctionService.buyout(player, holder.getListingId());
            switch (result) {
                case SUCCESS -> { /* 成功メッセージはサービス内で送信済み */ }
                case NOT_AVAILABLE -> messages.send(player, "buyout.not-available");
                case OWN_LISTING -> messages.send(player, "bid.own-listing");
                case NOT_ACTIVE -> messages.send(player, "bid.not-active");
                case INSUFFICIENT_FUNDS -> messages.send(player, "buyout.insufficient-funds");
                case FAILED -> messages.send(player, "error.generic");
            }
            guiManager.openMainMenu(player);
        }
    }

    private void handleMyListings(Player player, MyListingsHolder holder, int slot) {
        if (slot == MyListingsHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        Integer listingId = holder.getSlotToListingId().get(slot);
        if (listingId == null) {
            return;
        }
        Boolean cancellable = holder.getSlotCancellable().get(slot);
        if (Boolean.TRUE.equals(cancellable)) {
            AuctionService.CancelResult result = auctionService.cancelListing(player, listingId);
            handleCancelResult(player, result);
            guiManager.openMyListings(player);
        } else {
            guiManager.openListingDetail(player, listingId);
        }
    }

    private void handleMyBids(Player player, MyBidsHolder holder, int slot) {
        if (slot == MyBidsHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        Integer listingId = holder.getSlotToListingId().get(slot);
        if (listingId != null) {
            guiManager.openListingDetail(player, listingId);
        }
    }

    private void handleVault(Player player, VaultHolder holder, int slot) {
        if (slot == VaultHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        if (slot == VaultHolder.SLOT_PREV) {
            guiManager.openVault(player, holder.getPage() - 1);
            return;
        }
        if (slot == VaultHolder.SLOT_NEXT) {
            guiManager.openVault(player, holder.getPage() + 1);
            return;
        }
        Integer vaultId = holder.getSlotToVaultId().get(slot);
        if (vaultId != null) {
            auctionService.receiveVaultItem(player, vaultId);
            guiManager.openVault(player, holder.getPage());
        }
    }

    private void handleCancelResult(Player player, AuctionService.CancelResult result) {
        switch (result) {
            case SUCCESS -> { /* メッセージはサービス内で送信済み */ }
            case HAS_BIDS -> messages.send(player, "cancel-listing.has-bids");
            case NOT_OWNER -> messages.send(player, "cancel-listing.not-owner");
            case NOT_ACTIVE, NOT_FOUND -> messages.send(player, "cancel-listing.not-active");
        }
    }
}
