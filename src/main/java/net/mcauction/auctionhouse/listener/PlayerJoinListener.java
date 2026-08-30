package net.mcauction.auctionhouse.listener;

import net.mcauction.auctionhouse.AuctionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final AuctionService auctionService;

    public PlayerJoinListener(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        auctionService.deliverQueuedNotifications(event.getPlayer());
    }
}
