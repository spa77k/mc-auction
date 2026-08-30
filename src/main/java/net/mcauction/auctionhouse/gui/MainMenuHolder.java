package net.mcauction.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class MainMenuHolder implements InventoryHolder {

    public static final int SLOT_BROWSE = 11;
    public static final int SLOT_SELL = 12;
    public static final int SLOT_MY_LISTINGS = 13;
    public static final int SLOT_MY_BIDS = 14;
    public static final int SLOT_VAULT = 15;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
