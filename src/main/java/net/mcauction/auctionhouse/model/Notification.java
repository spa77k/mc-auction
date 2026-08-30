package net.mcauction.auctionhouse.model;

import java.util.UUID;

public class Notification {

    private final int id;
    private final UUID ownerUuid;
    private final String message;
    private final long createdAt;

    public Notification(int id, UUID ownerUuid, String message, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.message = message;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
