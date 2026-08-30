package net.mcauction.auctionhouse.model;

import java.util.UUID;

public class Listing {

    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final String itemData;
    private final String displayName;
    private final int amount;
    private final int startPrice;
    private final int buyoutPrice;
    private int currentPrice;
    private int bidCount;
    private UUID topBidderUuid;
    private String topBidderName;
    private final long createdAt;
    private long endAt;
    private int extensionCount;
    private ListingStatus status;
    private Long endedAt;
    private final int listingFee;
    private int saleFee;

    public Listing(int id, UUID sellerUuid, String sellerName, String itemData, String displayName,
                    int amount, int startPrice, int buyoutPrice, int currentPrice, int bidCount,
                    UUID topBidderUuid, String topBidderName, long createdAt, long endAt,
                    int extensionCount, ListingStatus status, Long endedAt, int listingFee, int saleFee) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemData = itemData;
        this.displayName = displayName;
        this.amount = amount;
        this.startPrice = startPrice;
        this.buyoutPrice = buyoutPrice;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.topBidderUuid = topBidderUuid;
        this.topBidderName = topBidderName;
        this.createdAt = createdAt;
        this.endAt = endAt;
        this.extensionCount = extensionCount;
        this.status = status;
        this.endedAt = endedAt;
        this.listingFee = listingFee;
        this.saleFee = saleFee;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getItemData() {
        return itemData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getAmount() {
        return amount;
    }

    public int getStartPrice() {
        return startPrice;
    }

    public int getBuyoutPrice() {
        return buyoutPrice;
    }

    public boolean hasBuyout() {
        return buyoutPrice > 0;
    }

    public int getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(int currentPrice) {
        this.currentPrice = currentPrice;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }

    public UUID getTopBidderUuid() {
        return topBidderUuid;
    }

    public void setTopBidderUuid(UUID topBidderUuid) {
        this.topBidderUuid = topBidderUuid;
    }

    public String getTopBidderName() {
        return topBidderName;
    }

    public void setTopBidderName(String topBidderName) {
        this.topBidderName = topBidderName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getEndAt() {
        return endAt;
    }

    public void setEndAt(long endAt) {
        this.endAt = endAt;
    }

    public int getExtensionCount() {
        return extensionCount;
    }

    public void setExtensionCount(int extensionCount) {
        this.extensionCount = extensionCount;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public Long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Long endedAt) {
        this.endedAt = endedAt;
    }

    public int getListingFee() {
        return listingFee;
    }

    public int getSaleFee() {
        return saleFee;
    }

    public void setSaleFee(int saleFee) {
        this.saleFee = saleFee;
    }
}
