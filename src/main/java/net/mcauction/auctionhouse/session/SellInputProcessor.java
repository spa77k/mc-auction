package net.mcauction.auctionhouse.session;

import net.mcauction.auctionhouse.AuctionService;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class SellInputProcessor {

    private final FileConfiguration config;
    private final SessionManager sessionManager;
    private final AuctionService auctionService;
    private final MessageUtil messages;

    public SellInputProcessor(FileConfiguration config, SessionManager sessionManager,
                               AuctionService auctionService, MessageUtil messages) {
        this.config = config;
        this.sessionManager = sessionManager;
        this.auctionService = auctionService;
        this.messages = messages;
    }

    public void handle(Player player, String input) {
        SellSession session = sessionManager.getSell(player.getUniqueId());
        if (session == null) {
            return;
        }

        int minPrice = config.getInt("auction.min-price", 100);
        int maxPrice = config.getInt("auction.max-price", 10_000_000);
        List<Integer> durations = durations();
        String trimmed = input == null ? "" : input.trim();

        switch (session.getStep()) {
            case AMOUNT -> {
                int amount;
                if (trimmed.isEmpty()) {
                    amount = session.getMaxAmount();
                } else {
                    try {
                        amount = Integer.parseInt(trimmed);
                    } catch (NumberFormatException e) {
                        messages.send(player, "sell.invalid-amount", Map.of("max", String.valueOf(session.getMaxAmount())));
                        return;
                    }
                }
                if (amount < 1 || amount > session.getMaxAmount()) {
                    messages.send(player, "sell.invalid-amount", Map.of("max", String.valueOf(session.getMaxAmount())));
                    return;
                }
                session.setAmount(amount);
                session.setStep(SellSession.Step.START_PRICE);
            }
            case START_PRICE -> {
                int price;
                try {
                    price = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    messages.send(player, "sell.invalid-start-price",
                            Map.of("min", String.valueOf(minPrice), "max", String.valueOf(maxPrice)));
                    return;
                }
                if (price < minPrice || price > maxPrice) {
                    messages.send(player, "sell.invalid-start-price",
                            Map.of("min", String.valueOf(minPrice), "max", String.valueOf(maxPrice)));
                    return;
                }
                session.setStartPrice(price);
                session.setStep(SellSession.Step.BUYOUT_PRICE);
            }
            case BUYOUT_PRICE -> {
                int buyout;
                if (trimmed.isEmpty() || trimmed.equals("0") || trimmed.equals("なし")) {
                    buyout = 0;
                } else {
                    try {
                        buyout = Integer.parseInt(trimmed);
                    } catch (NumberFormatException e) {
                        messages.send(player, "sell.invalid-buyout-price",
                                Map.of("min", String.valueOf(session.getStartPrice())));
                        return;
                    }
                    if (buyout <= session.getStartPrice() || buyout > maxPrice) {
                        messages.send(player, "sell.invalid-buyout-price",
                                Map.of("min", String.valueOf(session.getStartPrice())));
                        return;
                    }
                }
                session.setBuyoutPrice(buyout);
                session.setStep(SellSession.Step.DURATION);
            }
            case DURATION -> {
                int hours;
                try {
                    hours = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    messages.send(player, "sell.invalid-duration", Map.of("options", joinDurations(durations)));
                    return;
                }
                if (!durations.contains(hours)) {
                    messages.send(player, "sell.invalid-duration", Map.of("options", joinDurations(durations)));
                    return;
                }
                session.setDurationHours(hours);
                session.setStep(SellSession.Step.CONFIRM);
            }
            case CONFIRM -> {
                if (isYes(trimmed)) {
                    AuctionService.SellConfirmResult result = auctionService.confirmSell(player, session);
                    switch (result) {
                        case OK -> sessionManager.end(player.getUniqueId());
                        case ITEM_CHANGED -> {
                            messages.send(player, "sell.item-changed");
                            sessionManager.end(player.getUniqueId());
                        }
                        case INSUFFICIENT_FUNDS -> {
                            messages.send(player, "sell.insufficient-funds",
                                    Map.of("fee", String.valueOf(auctionService.calculateListingFee(session.getStartPrice()))));
                            sessionManager.end(player.getUniqueId());
                        }
                        case FAILED -> {
                            messages.send(player, "error.generic");
                            sessionManager.end(player.getUniqueId());
                        }
                    }
                } else if (isNo(trimmed)) {
                    messages.send(player, "sell.cancelled-confirm");
                    sessionManager.end(player.getUniqueId());
                } else {
                    messages.send(player, "sell.invalid-confirm");
                }
            }
        }
    }

    public String promptText(SellSession session) {
        if (session == null) {
            return "";
        }
        int minPrice = config.getInt("auction.min-price", 100);
        int maxPrice = config.getInt("auction.max-price", 10_000_000);
        List<Integer> durations = durations();

        return switch (session.getStep()) {
            case AMOUNT -> messages.get("prefix")
                    + messages.get("sell.ask-amount", Map.of("max", String.valueOf(session.getMaxAmount())));
            case START_PRICE -> messages.get("prefix") + messages.get("sell.ask-start-price",
                    Map.of("min", String.valueOf(minPrice), "max", String.valueOf(maxPrice)));
            case BUYOUT_PRICE -> messages.get("prefix") + messages.get("sell.ask-buyout-price");
            case DURATION -> messages.get("prefix")
                    + messages.get("sell.ask-duration", Map.of("options", joinDurations(durations)));
            case CONFIRM -> messages.get("prefix") + messages.get("sell.ask-confirm",
                    Map.of("fee", String.valueOf(auctionService.calculateListingFee(session.getStartPrice()))));
        };
    }

    private List<Integer> durations() {
        List<Integer> durations = config.getIntegerList("auction.durations-hours");
        if (durations.isEmpty()) {
            return List.of(6, 12, 24, 48);
        }
        return durations;
    }

    private boolean isYes(String s) {
        return s.equals("はい") || s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes");
    }

    private boolean isNo(String s) {
        return s.equals("いいえ") || s.equalsIgnoreCase("n") || s.equalsIgnoreCase("no");
    }

    private String joinDurations(List<Integer> durations) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < durations.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(durations.get(i));
        }
        return sb.toString();
    }
}
