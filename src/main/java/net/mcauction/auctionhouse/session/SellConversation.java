package net.mcauction.auctionhouse.session;

import net.mcauction.auctionhouse.AuctionService;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class SellConversation {

    private final SessionManager sessionManager;
    private final SellInputProcessor processor;
    private final AuctionService auctionService;
    private final MessageUtil messages;
    private final FileConfiguration config;
    private final ConversationFactory factory;

    public SellConversation(Plugin plugin, FileConfiguration config, SessionManager sessionManager,
                             SellInputProcessor processor, AuctionService auctionService, MessageUtil messages) {
        this.sessionManager = sessionManager;
        this.processor = processor;
        this.auctionService = auctionService;
        this.messages = messages;
        this.config = config;
        this.factory = new ConversationFactory(plugin)
                .withModality(false)
                .withLocalEcho(false)
                .withTimeout(config.getInt("auction.input-timeout-seconds", 120))
                .withFirstPrompt(new AskPrompt())
                .addConversationAbandonedListener(this::onAbandoned);
    }

    public void start(Player player) {
        AuctionService.SellStartResult result = auctionService.canStartSell(player);
        switch (result) {
            case EMPTY_HAND -> {
                messages.send(player, "sell.empty-hand");
                return;
            }
            case BANNED_MATERIAL -> {
                messages.send(player, "sell.banned-material");
                return;
            }
            case LIMIT_REACHED -> {
                int max = config.getInt("auction.max-listings-per-player", 3);
                messages.send(player, "sell.limit-reached", Map.of("max", String.valueOf(max)));
                return;
            }
            case OK -> {
                // continue
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack snapshot = hand.clone();
        int maxAmount = hand.getAmount();

        sessionManager.startSell(player.getUniqueId(), new SellSession(snapshot, maxAmount));
        player.closeInventory();
        messages.send(player, "sell.start");
        Conversation conversation = factory.buildConversation(player);
        sessionManager.attach(player.getUniqueId(), conversation);
        player.beginConversation(conversation);
    }

    private void onAbandoned(ConversationAbandonedEvent event) {
        if (event.gracefulExit()) {
            return;
        }
        if (!(event.getContext().getForWhom() instanceof Player player)) {
            return;
        }
        if (sessionManager.getSell(player.getUniqueId()) != null) {
            sessionManager.end(player.getUniqueId());
            messages.send(player, "sell.input-timeout");
        }
    }

    private class AskPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {
            if (!(context.getForWhom() instanceof Player player)) {
                return "";
            }
            SellSession session = sessionManager.getSell(player.getUniqueId());
            return processor.promptText(session);
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (!(context.getForWhom() instanceof Player player)) {
                return Prompt.END_OF_CONVERSATION;
            }
            processor.handle(player, input == null ? "" : input.trim());
            if (sessionManager.getSell(player.getUniqueId()) == null) {
                return Prompt.END_OF_CONVERSATION;
            }
            return this;
        }
    }
}
