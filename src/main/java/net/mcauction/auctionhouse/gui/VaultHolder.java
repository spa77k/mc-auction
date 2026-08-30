package net.mcauction.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class VaultHolder implements InventoryHolder {

    public static final int SLOT_PREV = 45;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_NEXT = 53;

    private Inventory inventory;
    private final Map<Integer, Integer> slotToVaultId = new HashMap<>();
    private int page;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, Integer> getSlotToVaultId() {
        return slotToVaultId;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
