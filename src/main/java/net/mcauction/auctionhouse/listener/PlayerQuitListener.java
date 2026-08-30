package net.mcauction.auctionhouse.listener;

import net.mcauction.auctionhouse.session.SessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final SessionManager sessionManager;

    public PlayerQuitListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.end(event.getPlayer().getUniqueId());
    }
}
