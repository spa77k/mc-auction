package net.mcauction.auctionhouse.session;

import org.bukkit.inventory.ItemStack;

public class SellSession {

    public enum Step {
        AMOUNT,
        START_PRICE,
        BUYOUT_PRICE,
        DURATION,
        CONFIRM
    }

    private Step step = Step.AMOUNT;
    private final ItemStack snapshotItem;
    private final int maxAmount;
    private int amount;
    private int startPrice;
    private int buyoutPrice;
    private int durationHours;

    public SellSession(ItemStack snapshotItem, int maxAmount) {
        this.snapshotItem = snapshotItem;
        this.maxAmount = maxAmount;
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
