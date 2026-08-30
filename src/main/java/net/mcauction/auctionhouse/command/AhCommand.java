package net.mcauction.auctionhouse.command;

import net.mcauction.auctionhouse.AuctionService;
import net.mcauction.auctionhouse.gui.GuiManager;
import net.mcauction.auctionhouse.model.ListingSort;
import net.mcauction.auctionhouse.session.SellConversation;
import net.mcauction.auctionhouse.session.SessionManager;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AhCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("sell", "list", "my", "bids", "vault", "cancel", "admin");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("cancel");

    private final GuiManager guiManager;
    private final SessionManager sessionManager;
    private final MessageUtil messages;
    private final SellConversation sellConversation;
    private final AuctionService auctionService;

    public AhCommand(GuiManager guiManager, SessionManager sessionManager, MessageUtil messages,
                      SellConversation sellConversation, AuctionService auctionService) {
        this.guiManager = guiManager;
        this.sessionManager = sessionManager;
        this.messages = messages;
        this.sellConversation = sellConversation;
        this.auctionService = auctionService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            guiManager.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sellConversation.start(player);
            case "list", "browse" -> guiManager.openListingList(player, 0, ListingSort.ENDING_SOON);
            case "my" -> guiManager.openMyListings(player);
            case "bids" -> guiManager.openMyBids(player);
            case "vault" -> guiManager.openVault(player, 0);
            case "cancel" -> handleCancel(player);
            case "admin" -> handleAdmin(player, args);
            default -> messages.send(player, "unknown-subcommand");
        }
        return true;
    }

    private void handleCancel(Player player) {
        if (sessionManager.getSell(player.getUniqueId()) != null) {
            sessionManager.end(player.getUniqueId());
            messages.send(player, "sell.cancelled-input");
        } else if (sessionManager.getBid(player.getUniqueId()) != null) {
            sessionManager.end(player.getUniqueId());
            messages.send(player, "bid.cancelled-input");
        } else {
            messages.send(player, "sell.no-active-session");
        }
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("auction.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("cancel")) {
            messages.send(player, "admin.usage");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messages.send(player, "admin.usage");
            return;
        }
        AuctionService.CancelResult result = auctionService.adminCancel(player, id);
        switch (result) {
            case SUCCESS -> { /* メッセージはサービス内で送信済み */ }
            case NOT_FOUND -> messages.send(player, "admin.not-found");
            case NOT_ACTIVE -> messages.send(player, "admin.not-active");
            default -> messages.send(player, "error.generic");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(prefix)) {
                    matches.add(subcommand);
                }
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String subcommand : ADMIN_SUBCOMMANDS) {
                if (subcommand.startsWith(prefix)) {
                    matches.add(subcommand);
                }
            }
            return matches;
        }
        return List.of();
    }
}
