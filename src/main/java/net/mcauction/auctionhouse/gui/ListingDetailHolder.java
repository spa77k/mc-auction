package net.mcauction.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ListingDetailHolder implements InventoryHolder {

    public static final int SLOT_BID = 11;
    public static final int SLOT_BUYOUT = 15;
    public static final int SLOT_CANCEL = 22;
    public static final int SLOT_BACK = 26;

    private Inventory inventory;
    private final int listingId;

    public ListingDetailHolder(int listingId) {
        this.listingId = listingId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int getListingId() {
        return listingId;
    }
}
