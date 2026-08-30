package net.mcauction.auctionhouse.task;

import net.mcauction.auctionhouse.AuctionService;
import org.bukkit.scheduler.BukkitRunnable;

public class AuctionCheckTask extends BukkitRunnable {

    private final AuctionService auctionService;

    public AuctionCheckTask(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Override
    public void run() {
        auctionService.processEndedAuctions();
        auctionService.purgeOldListings();
    }
}
