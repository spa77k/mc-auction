package net.mcauction.auctionhouse.storage;

import net.mcauction.auctionhouse.model.Notification;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationRepository {

    private final Database database;

    public NotificationRepository(Database database) {
        this.database = database;
    }

    public void insert(UUID ownerUuid, String message, long createdAt) throws SQLException {
        String sql = "INSERT INTO notifications (owner_uuid, message, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, message);
            statement.setLong(3, createdAt);
            statement.executeUpdate();
        }
    }

    public List<Notification> findByOwner(UUID ownerUuid) throws SQLException {
        String sql = "SELECT * FROM notifications WHERE owner_uuid = ? ORDER BY created_at ASC";
        List<Notification> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new Notification(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getString("message"),
                            rs.getLong("created_at")));
                }
            }
        }
        return result;
    }

    public void deleteByOwner(UUID ownerUuid) throws SQLException {
        String sql = "DELETE FROM notifications WHERE owner_uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            statement.executeUpdate();
        }
    }
}
