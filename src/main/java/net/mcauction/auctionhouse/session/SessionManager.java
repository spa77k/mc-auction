package net.mcauction.auctionhouse.session;

import org.bukkit.conversations.Conversation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<UUID, SellSession> sellSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BidSession> bidSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    public boolean has(UUID playerId) {
        return sellSessions.containsKey(playerId) || bidSessions.containsKey(playerId);
    }

    public void startSell(UUID playerId, SellSession session) {
        end(playerId);
        sellSessions.put(playerId, session);
    }

    public SellSession getSell(UUID playerId) {
        return sellSessions.get(playerId);
    }

    public void startBid(UUID playerId, BidSession session) {
        end(playerId);
        bidSessions.put(playerId, session);
    }

    public BidSession getBid(UUID playerId) {
        return bidSessions.get(playerId);
    }

    public void attach(UUID playerId, Conversation conversation) {
        conversations.put(playerId, conversation);
    }

    public void end(UUID playerId) {
        sellSessions.remove(playerId);
        bidSessions.remove(playerId);
        Conversation conversation = conversations.remove(playerId);
        if (conversation != null) {
            conversation.abandon();
        }
    }
}
