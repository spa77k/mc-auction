package net.mcauction.auctionhouse;

import net.mcauction.auctionhouse.command.AhCommand;
import net.mcauction.auctionhouse.economy.EconomyService;
import net.mcauction.auctionhouse.gui.GuiManager;
import net.mcauction.auctionhouse.listener.ChatInputListener;
import net.mcauction.auctionhouse.listener.GuiListener;
import net.mcauction.auctionhouse.listener.PlayerJoinListener;
import net.mcauction.auctionhouse.listener.PlayerQuitListener;
import net.mcauction.auctionhouse.session.BidConversation;
import net.mcauction.auctionhouse.session.BidInputProcessor;
import net.mcauction.auctionhouse.session.SellConversation;
import net.mcauction.auctionhouse.session.SellInputProcessor;
import net.mcauction.auctionhouse.session.SessionManager;
import net.mcauction.auctionhouse.storage.BidRepository;
import net.mcauction.auctionhouse.storage.Database;
import net.mcauction.auctionhouse.storage.ListingRepository;
import net.mcauction.auctionhouse.storage.NotificationRepository;
import net.mcauction.auctionhouse.storage.VaultRepository;
import net.mcauction.auctionhouse.task.AuctionCheckTask;
import net.mcauction.auctionhouse.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class AuctionHousePlugin extends JavaPlugin {

    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();

        database = new Database(this);
        try {
            database.connect();
        } catch (SQLException e) {
            getLogger().severe("データベースの初期化に失敗しました。プラグインを無効化します: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ListingRepository listingRepository = new ListingRepository(database);
        BidRepository bidRepository = new BidRepository(database);
        VaultRepository vaultRepository = new VaultRepository(database);
        NotificationRepository notificationRepository = new NotificationRepository(database);
        MessageUtil messages = new MessageUtil(getConfig());

        EconomyService economyService = new EconomyService();
        if (!economyService.setup(this)) {
            getLogger().warning("Vault経済プラグインが見つかりません。出品・入札・落札の支払いが機能しません。");
        }

        AuctionService auctionService = new AuctionService(listingRepository, bidRepository, vaultRepository,
                notificationRepository, economyService, messages, getConfig(), getLogger());
        auctionService.validateListingsOnStartup();

        GuiManager guiManager = new GuiManager(auctionService, economyService, messages, getLogger());
        SessionManager sessionManager = new SessionManager();
        SellInputProcessor sellInputProcessor = new SellInputProcessor(getConfig(), sessionManager, auctionService, messages);
        BidInputProcessor bidInputProcessor = new BidInputProcessor(sessionManager, auctionService, messages);
        SellConversation sellConversation = new SellConversation(this, getConfig(), sessionManager,
                sellInputProcessor, auctionService, messages);
        BidConversation bidConversation = new BidConversation(this, getConfig(), sessionManager,
                bidInputProcessor, messages);

        AhCommand ahCommand = new AhCommand(guiManager, sessionManager, messages, sellConversation, auctionService);
        getCommand("ah").setExecutor(ahCommand);
        getCommand("ah").setTabCompleter(ahCommand);

        getServer().getPluginManager().registerEvents(
                new GuiListener(guiManager, auctionService, sellConversation, bidConversation, messages, this), this);
        getServer().getPluginManager().registerEvents(
                new ChatInputListener(this, sessionManager, sellInputProcessor, bidInputProcessor), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(sessionManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(auctionService), this);

        long intervalTicks = Math.max(1L, getConfig().getLong("check-interval-seconds", 5) * 20L);
        new AuctionCheckTask(auctionService).runTaskTimer(this, intervalTicks, intervalTicks);

        getLogger().info("AuctionHouseが有効化されました。");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }
}
