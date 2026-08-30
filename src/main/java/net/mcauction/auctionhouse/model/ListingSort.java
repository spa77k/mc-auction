package net.mcauction.auctionhouse.model;

public enum ListingSort {
    ENDING_SOON("end_at ASC"),
    NEWEST("created_at DESC"),
    PRICE_LOW("current_price ASC"),
    PRICE_HIGH("current_price DESC");

    private final String orderBy;

    ListingSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderByClause() {
        return orderBy;
    }

    public ListingSort next() {
        ListingSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
