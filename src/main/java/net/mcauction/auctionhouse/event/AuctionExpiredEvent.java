package net.mcauction.auctionhouse.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/** 入札がないまま出品期間が終了したときの外部通知用イベント。 */
public final class AuctionExpiredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Map<String, String> placeholders;

    public AuctionExpiredEvent(String seller, String item, int amount) {
        placeholders = Map.of("player", seller, "seller", seller, "item", item,
                "amount", String.valueOf(amount));
    }

    public String getNotifyKind() { return "auction.expired"; }
    public Map<String, String> getNotifyPlaceholders() { return placeholders; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
