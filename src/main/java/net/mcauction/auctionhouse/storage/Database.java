package net.mcauction.auctionhouse.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private final Plugin plugin;
    private Connection connection;

    public Database(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "auction.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS listings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_data TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    start_price INTEGER NOT NULL,
                    buyout_price INTEGER NOT NULL,
                    current_price INTEGER NOT NULL,
                    bid_count INTEGER NOT NULL DEFAULT 0,
                    top_bidder_uuid TEXT,
                    top_bidder_name TEXT,
                    created_at INTEGER NOT NULL,
                    end_at INTEGER NOT NULL,
                    extension_count INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL,
                    ended_at INTEGER,
                    listing_fee INTEGER NOT NULL,
                    sale_fee INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS bids (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    listing_id INTEGER NOT NULL,
                    bidder_uuid TEXT NOT NULL,
                    bidder_name TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS vault_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    item_data TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    message TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_status_end "
                    + "ON listings(status, end_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_vault_items_owner "
                    + "ON vault_items(owner_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_notifications_owner "
                    + "ON notifications(owner_uuid)");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("データベースのクローズに失敗しました: " + e.getMessage());
            }
        }
    }
}
