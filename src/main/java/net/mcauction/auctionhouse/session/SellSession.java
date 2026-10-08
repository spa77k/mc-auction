package net.mcauction.auctionhouse.session;

import org.bukkit.inventory.ItemStack;

public class SellSession {

    public enum Step {
        START_PRICE,
        CONFIRM,
        AMOUNT,
        BUYOUT_PRICE,
        DURATION
    }

    private Step step = Step.START_PRICE;
    private final ItemStack snapshotItem;
    private final int maxAmount;
    private final int inventorySlot;
    private int amount;
    private int startPrice;
    private int buyoutPrice;
    private int durationHours;

    public SellSession(ItemStack snapshotItem, int maxAmount) {
        this(snapshotItem, maxAmount, -1);
    }

    public SellSession(ItemStack snapshotItem, int maxAmount, int inventorySlot) {
        this.snapshotItem = snapshotItem;
        this.maxAmount = maxAmount;
        this.inventorySlot = inventorySlot;
        this.amount = maxAmount;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public ItemStack getSnapshotItem() {
        return snapshotItem;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public int getInventorySlot() {
        return inventorySlot;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(int startPrice) {
        this.startPrice = startPrice;
    }

    public int getBuyoutPrice() {
        return buyoutPrice;
    }

    public void setBuyoutPrice(int buyoutPrice) {
        this.buyoutPrice = buyoutPrice;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }
}
