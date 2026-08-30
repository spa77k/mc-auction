package net.mcauction.auctionhouse.session;

import net.mcauction.auctionhouse.AuctionService;
import net.mcauction.auctionhouse.model.Listing;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;

public class BidInputProcessor {

    private final SessionManager sessionManager;
    private final AuctionService auctionService;
    private final MessageUtil messages;

    public BidInputProcessor(SessionManager sessionManager, AuctionService auctionService, MessageUtil messages) {
        this.sessionManager = sessionManager;
        this.auctionService = auctionService;
        this.messages = messages;
    }

    public void handle(Player player, String input) {
        BidSession session = sessionManager.getBid(player.getUniqueId());
        if (session == null) {
            return;
        }
        String trimmed = input == null ? "" : input.trim();

        int amount;
        try {
            amount = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            messages.send(player, "bid.invalid-amount", Map.of("min", String.valueOf(currentMinBid(session))));
            return;
        }

        AuctionService.BidResult result = auctionService.placeBid(player, session.getListingId(), amount);
        switch (result) {
            case SUCCESS, BOUGHT_OUT -> sessionManager.end(player.getUniqueId());
            case INVALID_TOO_LOW -> messages.send(player, "bid.invalid-amount",
                    Map.of("min", String.valueOf(currentMinBid(session))));
            case INSUFFICIENT_FUNDS -> {
                messages.send(player, "bid.insufficient-funds", Map.of("amount", String.valueOf(amount)));
                sessionManager.end(player.getUniqueId());
            }
            case OWN_LISTING -> {
                messages.send(player, "bid.own-listing");
                sessionManager.end(player.getUniqueId());
            }
            case NOT_ACTIVE -> {
                messages.send(player, "bid.not-active");
                sessionManager.end(player.getUniqueId());
            }
            case FAILED -> {
                messages.send(player, "error.generic");
                sessionManager.end(player.getUniqueId());
            }
        }
    }

    public String promptText(BidSession session) {
        if (session == null) {
            return "";
        }
        return messages.get("prefix") + messages.get("bid.start", Map.of("min", String.valueOf(currentMinBid(session))));
    }

    private int currentMinBid(BidSession session) {
        Listing listing = auctionService.findListing(session.getListingId());
        return listing != null ? auctionService.calculateMinBid(listing) : 0;
    }
}
