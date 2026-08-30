package net.mcauction.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class BuyoutConfirmHolder implements InventoryHolder {

    public static final int SLOT_CONFIRM = 11;
    public static final int SLOT_CANCEL = 15;

    private Inventory inventory;
    private final int listingId;

    public BuyoutConfirmHolder(int listingId) {
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
