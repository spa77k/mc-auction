package net.mcauction.auctionhouse.model;

import java.util.UUID;

public class VaultItem {

    private final int id;
    private final UUID ownerUuid;
    private final String ownerName;
    private String itemData;
    private int amount;
    private final String reason;
    private final long createdAt;

    public VaultItem(int id, UUID ownerUuid, String ownerName, String itemData, int amount,
                      String reason, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.itemData = itemData;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
