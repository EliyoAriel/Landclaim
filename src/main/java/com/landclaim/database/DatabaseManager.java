package com.landclaim.database;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimFlag;
import com.landclaim.claim.ClaimPermission;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DatabaseManager {

    protected final LandClaimPlugin plugin;
    protected HikariDataSource dataSource;

    public DatabaseManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract void init() throws Exception;
    public abstract void close();

    protected abstract String claimsTableDDL();
    protected abstract String permissionsTableDDL();
    protected abstract String flagsTableDDL();
    protected abstract String globalTrustDDL();
    protected abstract String claimNamesTableDDL();
    protected abstract String claimBansTableDDL();
    protected abstract String claimLogsTableDDL();
    protected abstract String playerSettingsTableDDL();

    protected abstract String[] spawnColumnMigrations();
    protected abstract String[] taxColumnMigrations();

    protected void initTables() {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement(claimsTableDDL()).executeUpdate();
            conn.prepareStatement(permissionsTableDDL()).executeUpdate();
            conn.prepareStatement(flagsTableDDL()).executeUpdate();
            conn.prepareStatement(globalTrustDDL()).executeUpdate();
            conn.prepareStatement(claimNamesTableDDL()).executeUpdate();
            conn.prepareStatement(claimBansTableDDL()).executeUpdate();
            conn.prepareStatement(claimLogsTableDDL()).executeUpdate();
            conn.prepareStatement(playerSettingsTableDDL()).executeUpdate();
            for (String sql : spawnColumnMigrations()) {
                try {
                    conn.prepareStatement(sql).executeUpdate();
                } catch (SQLException ignored) {}
            }
            for (String sql : taxColumnMigrations()) {
                try {
                    conn.prepareStatement(sql).executeUpdate();
                } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    public void saveClaim(Claim claim) {
        String upsert = "INSERT OR REPLACE INTO claims (owner_uuid, world, center_x, center_z, radius, tier, " +
                "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, tax_next_due, tax_grace_end) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, claim.getOwner().toString());
            ps.setString(2, claim.getWorld().getName());
            ps.setInt(3, claim.getCenterX());
            ps.setInt(4, claim.getCenterZ());
            ps.setInt(5, claim.getRadius());
            ps.setInt(6, claim.getTier());
            if (claim.hasCustomSpawn()) {
                Location loc = claim.getSpawnLocation();
                ps.setInt(7, loc.getBlockX());
                ps.setInt(8, loc.getBlockY());
                ps.setInt(9, loc.getBlockZ());
                ps.setFloat(10, loc.getYaw());
                ps.setFloat(11, loc.getPitch());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
                ps.setNull(8, java.sql.Types.INTEGER);
                ps.setNull(9, java.sql.Types.INTEGER);
                ps.setNull(10, java.sql.Types.REAL);
                ps.setNull(11, java.sql.Types.REAL);
            }
            long nextDue = claim.getTaxNextDue();
            if (nextDue > 0) {
                ps.setLong(12, nextDue);
            } else {
                ps.setNull(12, java.sql.Types.BIGINT);
            }
            long graceEnd = claim.getTaxGraceEnd();
            if (graceEnd > 0) {
                ps.setLong(13, graceEnd);
            } else {
                ps.setNull(13, java.sql.Types.BIGINT);
            }
            ps.executeUpdate();

            savePermissions(claim, conn);
            saveFlags(claim, conn);
            saveName(claim, conn);
            saveBans(claim, conn);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save claim: " + e.getMessage());
        }
    }

    private void saveName(Claim claim, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claim_names WHERE owner_uuid = ?")) {
            ps.setString(1, claim.getOwner().toString());
            ps.executeUpdate();
        }
        if (claim.getName() == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claim_names (owner_uuid, name) VALUES (?, ?)")) {
            ps.setString(1, claim.getOwner().toString());
            ps.setString(2, claim.getName());
            ps.executeUpdate();
        }
    }

    private void saveBans(Claim claim, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claim_bans WHERE owner_uuid = ?")) {
            ps.setString(1, claim.getOwner().toString());
            ps.executeUpdate();
        }
        String insert = "INSERT INTO claim_bans (owner_uuid, banned_uuid) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            for (UUID banned : claim.getBannedPlayers()) {
                ps.setString(1, claim.getOwner().toString());
                ps.setString(2, banned.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void savePermissions(Claim claim, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM claim_permissions WHERE owner_uuid = ?")) {
            ps.setString(1, claim.getOwner().toString());
            ps.executeUpdate();
        }
        String insert = "INSERT INTO claim_permissions (owner_uuid, trusted_uuid, permission) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            for (Map.Entry<UUID, EnumSet<ClaimPermission>> entry : claim.getAllowedPlayers().entrySet()) {
                UUID trusted = entry.getKey();
                for (ClaimPermission perm : entry.getValue()) {
                    ps.setString(1, claim.getOwner().toString());
                    ps.setString(2, trusted.toString());
                    ps.setString(3, perm.name());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void saveFlags(Claim claim, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM claim_flags WHERE owner_uuid = ?")) {
            ps.setString(1, claim.getOwner().toString());
            ps.executeUpdate();
        }
        String insert = "INSERT INTO claim_flags (owner_uuid, flag_name, flag_value) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
                ps.setString(1, claim.getOwner().toString());
                ps.setString(2, entry.getKey().name());
                ps.setBoolean(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void removeClaim(UUID owner) {
        try (Connection conn = dataSource.getConnection()) {
            for (String table : new String[]{"claim_permissions", "claim_flags", "claim_names", "claim_bans", "claims"}) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + table + " WHERE owner_uuid = ?")) {
                    ps.setString(1, owner.toString());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove claim: " + e.getMessage());
        }
    }

    public void transferClaim(UUID oldOwner, UUID newOwner) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String update = "UPDATE claims SET owner_uuid = ? WHERE owner_uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setString(1, newOwner.toString());
                    ps.setString(2, oldOwner.toString());
                    ps.executeUpdate();
                }
                for (String table : new String[]{"claim_permissions", "claim_flags", "claim_names", "claim_bans"}) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE " + table + " SET owner_uuid = ? WHERE owner_uuid = ?")) {
                        ps.setString(1, newOwner.toString());
                        ps.setString(2, oldOwner.toString());
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to transfer claim: " + e.getMessage());
        }
    }

    public Map<UUID, Claim> loadAllClaims() {
        Map<UUID, Claim> claims = new HashMap<>();
        String select = "SELECT * FROM claims";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                String worldName = rs.getString("world");
                int x = rs.getInt("center_x");
                int z = rs.getInt("center_z");
                int radius = rs.getInt("radius");
                int tier = rs.getInt("tier");
                org.bukkit.World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World " + worldName + " not found for claim of " + owner);
                    continue;
                }
                Claim claim = new Claim(owner, world, x, z, radius, tier);
                loadSpawn(claim, rs);
                loadTax(claim, rs);
                loadPermissions(claim, conn);
                loadFlags(claim, conn);
                loadName(claim, conn);
                loadBans(claim, conn);
                claims.put(owner, claim);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load claims: " + e.getMessage());
        }
        return claims;
    }

    private void loadSpawn(Claim claim, ResultSet rs) throws SQLException {
        int spawnX = rs.getInt("spawn_x");
        if (!rs.wasNull()) {
            int spawnY = rs.getInt("spawn_y");
            int spawnZ = rs.getInt("spawn_z");
            float yaw = rs.getFloat("spawn_yaw");
            float pitch = rs.getFloat("spawn_pitch");
            Location loc = new Location(claim.getWorld(), spawnX + 0.5, spawnY, spawnZ + 0.5, yaw, pitch);
            claim.setSpawnLocation(loc);
        }
    }

    private void loadTax(Claim claim, ResultSet rs) throws SQLException {
        long nextDue = rs.getLong("tax_next_due");
        if (!rs.wasNull()) {
            claim.setTaxNextDue(nextDue);
        }
        long graceEnd = rs.getLong("tax_grace_end");
        if (!rs.wasNull()) {
            claim.setTaxGraceEnd(graceEnd);
        }
    }

    private void loadName(Claim claim, Connection conn) throws SQLException {
        String select = "SELECT name FROM claim_names WHERE owner_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, claim.getOwner().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    claim.setName(rs.getString("name"));
                }
            }
        }
    }

    private void loadBans(Claim claim, Connection conn) throws SQLException {
        String select = "SELECT banned_uuid FROM claim_bans WHERE owner_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, claim.getOwner().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        claim.getBannedPlayers().add(UUID.fromString(rs.getString("banned_uuid")));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    private void loadPermissions(Claim claim, Connection conn) throws SQLException {
        String select = "SELECT trusted_uuid, permission FROM claim_permissions WHERE owner_uuid = ?";
        Map<UUID, Set<ClaimPermission>> perms = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, claim.getOwner().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID trusted = UUID.fromString(rs.getString("trusted_uuid"));
                    ClaimPermission perm = ClaimPermission.valueOf(rs.getString("permission"));
                    perms.computeIfAbsent(trusted, k -> EnumSet.noneOf(ClaimPermission.class)).add(perm);
                }
            }
        }
        for (Map.Entry<UUID, Set<ClaimPermission>> entry : perms.entrySet()) {
            if (entry.getValue().size() == ClaimPermission.values().length) {
                claim.addAllowed(entry.getKey());
            } else {
                claim.addAllowed(entry.getKey(), entry.getValue());
            }
        }
    }

    private void loadFlags(Claim claim, Connection conn) throws SQLException {
        String select = "SELECT flag_name, flag_value FROM claim_flags WHERE owner_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, claim.getOwner().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        ClaimFlag flag = ClaimFlag.valueOf(rs.getString("flag_name"));
                        boolean value = rs.getBoolean("flag_value");
                        claim.setFlag(flag, value);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    public Set<UUID> loadGlobalTrust() {
        Set<UUID> trusted = ConcurrentHashMap.newKeySet();
        String select = "SELECT player_uuid FROM global_trust";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                trusted.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load global trust: " + e.getMessage());
        }
        return trusted;
    }

    public void addGlobalTrust(UUID player) {
        String insert = "INSERT OR IGNORE INTO global_trust (player_uuid) VALUES (?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add global trust: " + e.getMessage());
        }
    }

    public void removeGlobalTrust(UUID player) {
        String delete = "DELETE FROM global_trust WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove global trust: " + e.getMessage());
        }
    }

    public boolean isGloballyTrusted(UUID player) {
        String select = "SELECT 1 FROM global_trust WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public void saveGlobalTrust(Set<UUID> trusted) {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM global_trust").executeUpdate();
            String insert = "INSERT INTO global_trust (player_uuid) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (UUID uuid : trusted) {
                    ps.setString(1, uuid.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save global trust: " + e.getMessage());
        }
    }

    public Set<UUID> loadNotifyDisabled() {
        Set<UUID> set = new HashSet<>();
        String select = "SELECT player_uuid FROM player_settings WHERE setting_key = 'notify' AND setting_value = 'false'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                set.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player settings: " + e.getMessage());
        }
        return set;
    }

    public void saveNotifyDisabled(UUID player, boolean disabled) {
        String upsert = "INSERT OR REPLACE INTO player_settings (player_uuid, setting_key, setting_value) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, player.toString());
            ps.setString(2, "notify");
            ps.setString(3, disabled ? "false" : "true");
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player setting: " + e.getMessage());
        }
    }

    public void insertLog(String ownerUuid, String action, String player, String details) {
        String insert = "INSERT INTO claim_logs (owner_uuid, action, player_name, details, timestamp) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, action);
            ps.setString(3, player);
            ps.setString(4, details);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to insert log: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> loadLogs(String ownerUuid, int limit) {
        List<Map<String, Object>> logs = new ArrayList<>();
        String select = "SELECT * FROM claim_logs WHERE owner_uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, ownerUuid);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", rs.getInt("id"));
                    entry.put("action", rs.getString("action"));
                    entry.put("player", rs.getString("player_name"));
                    entry.put("details", rs.getString("details"));
                    entry.put("timestamp", rs.getLong("timestamp"));
                    logs.add(entry);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load logs: " + e.getMessage());
        }
        return logs;
    }
}
