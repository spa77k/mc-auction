package net.mcauction.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MyBidsHolder implements InventoryHolder {

    public static final int SLOT_BACK = 49;

    private Inventory inventory;
    private final Map<Integer, Integer> slotToListingId = new HashMap<>();

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, Integer> getSlotToListingId() {
        return slotToListingId;
    }
}
