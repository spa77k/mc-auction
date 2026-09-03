package net.mcauction.auctionhouse.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 新規出品が成立した直後にメインスレッドで発火するイベント。徴収・回収・保存がすべて成功した場合にのみ発火する。 */
public class AuctionListedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int listingId;
    private final UUID sellerUuid;
    private final String sellerName;
    private final String displayName;
    private final int amount;
    private final int startPrice;
    private final int buyoutPrice;
    private final int durationHours;
    private final Map<String, String> notifyPlaceholders;

    public AuctionListedEvent(int listingId, UUID sellerUuid, String sellerName, String displayName,
                               int amount, int startPrice, int buyoutPrice, int durationHours,
                               String startPriceFormatted, String buyoutPriceFormatted) {
        this.listingId = listingId;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.displayName = displayName;
        this.amount = amount;
        this.startPrice = startPrice;
        this.buyoutPrice = buyoutPrice;
        this.durationHours = durationHours;

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", sellerName);
        placeholders.put("item", displayName);
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("start_price", startPriceFormatted);
        placeholders.put("buyout_price", buyoutPrice > 0 ? buyoutPriceFormatted : "");
        placeholders.put("duration_hours", String.valueOf(durationHours));
        this.notifyPlaceholders = Collections.unmodifiableMap(placeholders);
    }

    public String getNotifyKind() {
        return "auction.listed";
    }

    public Map<String, String> getNotifyPlaceholders() {
        return notifyPlaceholders;
    }

    public int getListingId() {
        return listingId;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
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

    public int getDurationHours() {
        return durationHours;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
