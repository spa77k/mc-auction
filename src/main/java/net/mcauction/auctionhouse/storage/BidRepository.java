package net.mcauction.auctionhouse.storage;

import net.mcauction.auctionhouse.model.Bid;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BidRepository {

    private final Database database;

    public BidRepository(Database database) {
        this.database = database;
    }

    public Bid insert(int listingId, UUID bidderUuid, String bidderName, int amount, long createdAt)
            throws SQLException {
        String sql = """
            INSERT INTO bids (listing_id, bidder_uuid, bidder_name, amount, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = database.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, listingId);
            statement.setString(2, bidderUuid.toString());
            statement.setString(3, bidderName);
            statement.setInt(4, amount);
            statement.setLong(5, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                return new Bid(id, listingId, bidderUuid, bidderName, amount, createdAt);
            }
        }
    }

    public List<Bid> findByListing(int listingId) throws SQLException {
        String sql = "SELECT * FROM bids WHERE listing_id = ? ORDER BY created_at DESC";
        List<Bid> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, listingId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new Bid(
                            rs.getInt("id"),
                            rs.getInt("listing_id"),
                            UUID.fromString(rs.getString("bidder_uuid")),
                            rs.getString("bidder_name"),
                            rs.getInt("amount"),
                            rs.getLong("created_at")));
                }
            }
        }
        return result;
    }
}
