package net.mcauction.auctionhouse.session;

public class BidSession {

    private final int listingId;

    public BidSession(int listingId) {
        this.listingId = listingId;
    }

    public int getListingId() {
        return listingId;
    }
}
