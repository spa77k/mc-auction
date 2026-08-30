package net.mcauction.auctionhouse.storage;

import net.mcauction.auctionhouse.model.Listing;
import net.mcauction.auctionhouse.model.ListingSort;
import net.mcauction.auctionhouse.model.ListingStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ListingRepository {

    private final Database database;

    public ListingRepository(Database database) {
        this.database = database;
    }

    public Listing insert(UUID sellerUuid, String sellerName, String itemData, String displayName,
                           int amount, int startPrice, int buyoutPrice, long createdAt, long endAt,
                           int listingFee) throws SQLException {
        String sql = """
            INSERT INTO listings (seller_uuid, seller_name, item_data, display_name, amount,
                start_price, buyout_price, current_price, bid_count, created_at, end_at,
                extension_count, status, listing_fee, sale_fee)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0, ?, ?, 0)
            """;
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sellerUuid.toString());
            statement.setString(2, sellerName);
            statement.setString(3, itemData);
            statement.setString(4, displayName);
            statement.setInt(5, amount);
            statement.setInt(6, startPrice);
            statement.setInt(7, buyoutPrice);
            statement.setInt(8, startPrice);
            statement.setLong(9, createdAt);
            statement.setLong(10, endAt);
            statement.setString(11, ListingStatus.ACTIVE.name());
            statement.setInt(12, listingFee);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                return new Listing(id, sellerUuid, sellerName, itemData, displayName, amount, startPrice,
                        buyoutPrice, startPrice, 0, null, null, createdAt, endAt, 0,
                        ListingStatus.ACTIVE, null, listingFee, 0);
            }
        }
    }

    public Listing findById(int id) throws SQLException {
        String sql = "SELECT * FROM listings WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public List<Listing> findActive(ListingSort sort) throws SQLException {
        String sql = "SELECT * FROM listings WHERE status = ? ORDER BY " + sort.orderByClause();
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ListingStatus.ACTIVE.name());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Listing> findActiveEndingBefore(long now) throws SQLException {
        String sql = "SELECT * FROM listings WHERE status = ? AND end_at <= ?";
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ListingStatus.ACTIVE.name());
            statement.setLong(2, now);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Listing> findBySeller(UUID sellerUuid) throws SQLException {
        String sql = "SELECT * FROM listings WHERE seller_uuid = ? ORDER BY created_at DESC";
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Listing> findByBidder(UUID bidderUuid) throws SQLException {
        String sql = """
            SELECT DISTINCT l.* FROM listings l
            INNER JOIN bids b ON b.listing_id = l.id
            WHERE b.bidder_uuid = ?
            ORDER BY l.end_at ASC
            """;
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, bidderUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public int countActiveBySeller(UUID sellerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM listings WHERE seller_uuid = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, sellerUuid.toString());
            statement.setString(2, ListingStatus.ACTIVE.name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void updateAfterBid(int id, int currentPrice, int bidCount, UUID topBidderUuid,
                                String topBidderName, long endAt, int extensionCount) throws SQLException {
        String sql = """
            UPDATE listings SET current_price = ?, bid_count = ?, top_bidder_uuid = ?,
                top_bidder_name = ?, end_at = ?, extension_count = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, currentPrice);
            statement.setInt(2, bidCount);
            statement.setString(3, topBidderUuid.toString());
            statement.setString(4, topBidderName);
            statement.setLong(5, endAt);
            statement.setInt(6, extensionCount);
            statement.setInt(7, id);
            statement.executeUpdate();
        }
    }

    public boolean markSold(int id, long endedAt, int saleFee) throws SQLException {
        String sql = "UPDATE listings SET status = ?, ended_at = ?, sale_fee = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ListingStatus.SOLD.name());
            statement.setLong(2, endedAt);
            statement.setInt(3, saleFee);
            statement.setInt(4, id);
            statement.setString(5, ListingStatus.ACTIVE.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean markExpired(int id, long endedAt) throws SQLException {
        String sql = "UPDATE listings SET status = ?, ended_at = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ListingStatus.EXPIRED.name());
            statement.setLong(2, endedAt);
            statement.setInt(3, id);
            statement.setString(4, ListingStatus.ACTIVE.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean markCancelled(int id, long endedAt) throws SQLException {
        String sql = "UPDATE listings SET status = ?, ended_at = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ListingStatus.CANCELLED.name());
            statement.setLong(2, endedAt);
            statement.setInt(3, id);
            statement.setString(4, ListingStatus.ACTIVE.name());
            return statement.executeUpdate() > 0;
        }
    }

    public int deleteClosedBefore(long closedBefore) throws SQLException {
        String sql = "DELETE FROM listings WHERE status IN (?, ?, ?) AND ended_at <= ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ListingStatus.SOLD.name());
            statement.setString(2, ListingStatus.EXPIRED.name());
            statement.setString(3, ListingStatus.CANCELLED.name());
            statement.setLong(4, closedBefore);
            return statement.executeUpdate();
        }
    }

    private Listing map(ResultSet rs) throws SQLException {
        String topBidderUuidStr = rs.getString("top_bidder_uuid");
        long endedAtRaw = rs.getLong("ended_at");
        Long endedAt = rs.wasNull() ? null : endedAtRaw;
        return new Listing(
                rs.getInt("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getString("item_data"),
                rs.getString("display_name"),
                rs.getInt("amount"),
                rs.getInt("start_price"),
                rs.getInt("buyout_price"),
                rs.getInt("current_price"),
                rs.getInt("bid_count"),
                topBidderUuidStr == null ? null : UUID.fromString(topBidderUuidStr),
                rs.getString("top_bidder_name"),
                rs.getLong("created_at"),
                rs.getLong("end_at"),
                rs.getInt("extension_count"),
                ListingStatus.valueOf(rs.getString("status")),
                endedAt,
                rs.getInt("listing_fee"),
                rs.getInt("sale_fee")
        );
    }
}
