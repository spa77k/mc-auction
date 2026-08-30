package net.mcauction.auctionhouse.session;

import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class BidConversation {

    private final SessionManager sessionManager;
    private final BidInputProcessor processor;
    private final MessageUtil messages;
    private final ConversationFactory factory;

    public BidConversation(Plugin plugin, FileConfiguration config, SessionManager sessionManager,
                            BidInputProcessor processor, MessageUtil messages) {
        this.sessionManager = sessionManager;
        this.processor = processor;
        this.messages = messages;
        this.factory = new ConversationFactory(plugin)
                .withModality(false)
                .withLocalEcho(false)
                .withTimeout(config.getInt("auction.input-timeout-seconds", 120))
                .withFirstPrompt(new AskPrompt())
                .addConversationAbandonedListener(this::onAbandoned);
    }

    public void start(Player player, int listingId) {
        sessionManager.startBid(player.getUniqueId(), new BidSession(listingId));
        player.closeInventory();
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
        if (sessionManager.getBid(player.getUniqueId()) != null) {
            sessionManager.end(player.getUniqueId());
            messages.send(player, "bid.input-timeout");
        }
    }

    private class AskPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {
            if (!(context.getForWhom() instanceof Player player)) {
                return "";
            }
            BidSession session = sessionManager.getBid(player.getUniqueId());
            return processor.promptText(session);
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (!(context.getForWhom() instanceof Player player)) {
                return Prompt.END_OF_CONVERSATION;
            }
            processor.handle(player, input == null ? "" : input.trim());
            if (sessionManager.getBid(player.getUniqueId()) == null) {
                return Prompt.END_OF_CONVERSATION;
            }
            return this;
        }
    }
}
