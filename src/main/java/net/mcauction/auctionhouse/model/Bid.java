package net.mcauction.auctionhouse.model;

import java.util.UUID;

public class Bid {

    private final int id;
    private final int listingId;
    private final UUID bidderUuid;
    private final String bidderName;
    private final int amount;
    private final long createdAt;

    public Bid(int id, int listingId, UUID bidderUuid, String bidderName, int amount, long createdAt) {
        this.id = id;
        this.listingId = listingId;
        this.bidderUuid = bidderUuid;
        this.bidderName = bidderName;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getListingId() {
        return listingId;
    }

    public UUID getBidderUuid() {
        return bidderUuid;
    }

    public String getBidderName() {
        return bidderName;
    }

    public int getAmount() {
        return amount;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
