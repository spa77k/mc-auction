package net.mcauction.auctionhouse.gui;

import net.mcauction.auctionhouse.model.ListingSort;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ListingListHolder implements InventoryHolder {

    public static final int SLOT_PREV = 45;
    public static final int SLOT_SORT = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_NEXT = 53;

    private Inventory inventory;
    private final Map<Integer, Integer> slotToListingId = new HashMap<>();
    private int page;
    private ListingSort sort = ListingSort.ENDING_SOON;

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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public ListingSort getSort() {
        return sort;
    }

    public void setSort(ListingSort sort) {
        this.sort = sort;
    }
}
