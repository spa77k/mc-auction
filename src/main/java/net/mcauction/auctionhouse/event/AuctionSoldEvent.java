package net.mcauction.auctionhouse.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/** 落札確定（通常終了・即決）時の外部通知用イベント。 */
public final class AuctionSoldEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Map<String, String> placeholders;

    public AuctionSoldEvent(String seller, String winner, String item, int amount, String price, boolean buyout) {
        placeholders = Map.of("player", winner, "seller", seller, "winner", winner,
                "item", item, "amount", String.valueOf(amount), "price", price,
                "method", buyout ? "即決" : "入札");
    }

    public String getNotifyKind() { return "auction.sold"; }
    public Map<String, String> getNotifyPlaceholders() { return placeholders; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
