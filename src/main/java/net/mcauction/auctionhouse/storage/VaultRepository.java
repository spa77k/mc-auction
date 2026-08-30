package net.mcauction.auctionhouse.storage;

import net.mcauction.auctionhouse.model.VaultItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VaultRepository {

    private final Database database;

    public VaultRepository(Database database) {
        this.database = database;
    }

    public VaultItem insert(UUID ownerUuid, String ownerName, String itemData, int amount, String reason,
                             long createdAt) throws SQLException {
        String sql = """
            INSERT INTO vault_items (owner_uuid, owner_name, item_data, amount, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = database.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, ownerName);
            statement.setString(3, itemData);
            statement.setInt(4, amount);
            statement.setString(5, reason);
            statement.setLong(6, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                return new VaultItem(id, ownerUuid, ownerName, itemData, amount, reason, createdAt);
            }
        }
    }

    public List<VaultItem> findByOwner(UUID ownerUuid) throws SQLException {
        String sql = "SELECT * FROM vault_items WHERE owner_uuid = ? ORDER BY created_at ASC";
        List<VaultItem> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void updateRemainder(int id, String itemData, int amount) throws SQLException {
        String sql = "UPDATE vault_items SET item_data = ?, amount = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, itemData);
            statement.setInt(2, amount);
            statement.setInt(3, id);
            statement.executeUpdate();
        }
    }

    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM vault_items WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private VaultItem map(ResultSet rs) throws SQLException {
        return new VaultItem(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("item_data"),
                rs.getInt("amount"),
                rs.getString("reason"),
                rs.getLong("created_at"));
    }
}
