package com.landclaim.database;

import com.landclaim.LandClaimPlugin;
import com.zaxxer.hikari.HikariConfig;

import java.io.File;

public class SQLiteDatabase extends DatabaseManager {

    public SQLiteDatabase(LandClaimPlugin plugin) {
        super(plugin);
    }

    @Override
    public void init() {
        String fileName = plugin.getConfig().getString("database.sqlite.file", "data.db");
        File dbFile = new File(plugin.getDataFolder(), fileName);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("LandClaim-SQLite");

        dataSource = new com.zaxxer.hikari.HikariDataSource(config);
        initTables();
        plugin.getLogger().info("Database: SQLite (" + dbFile.getName() + ")");
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    protected String claimsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claims (" +
                "owner_uuid VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(255) NOT NULL," +
                "center_x INT NOT NULL," +
                "center_z INT NOT NULL," +
                "radius INT NOT NULL DEFAULT 5," +
                "tier INT NOT NULL DEFAULT 1," +
                "spawn_x INT," +
                "spawn_y INT," +
                "spawn_z INT," +
                "spawn_yaw REAL," +
                "spawn_pitch REAL," +
                "tax_next_due BIGINT DEFAULT 0," +
                "tax_grace_end BIGINT DEFAULT 0" +
                ")";
    }

    @Override
    protected String permissionsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_permissions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "trusted_uuid VARCHAR(36) NOT NULL," +
                "permission VARCHAR(20) NOT NULL," +
                "UNIQUE(owner_uuid, trusted_uuid, permission)" +
                ")";
    }

    @Override
    protected String flagsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_flags (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "flag_name VARCHAR(50) NOT NULL," +
                "flag_value INT NOT NULL DEFAULT 1," +
                "UNIQUE(owner_uuid, flag_name)" +
                ")";
    }

    @Override
    protected String globalTrustDDL() {
        return "CREATE TABLE IF NOT EXISTS global_trust (" +
                "player_uuid VARCHAR(36) PRIMARY KEY" +
                ")";
    }

    @Override
    protected String claimNamesTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_names (" +
                "owner_uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(255) NOT NULL" +
                ")";
    }

    @Override
    protected String claimBansTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_bans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "banned_uuid VARCHAR(36) NOT NULL," +
                "UNIQUE(owner_uuid, banned_uuid)" +
                ")";
    }

    @Override
    protected String playerSettingsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS player_settings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "setting_key VARCHAR(50) NOT NULL," +
                "setting_value VARCHAR(255) NOT NULL," +
                "UNIQUE(player_uuid, setting_key)" +
                ")";
    }

    @Override
    protected String[] spawnColumnMigrations() {
        return new String[]{
            "ALTER TABLE claims ADD COLUMN spawn_x INT",
            "ALTER TABLE claims ADD COLUMN spawn_y INT",
            "ALTER TABLE claims ADD COLUMN spawn_z INT",
            "ALTER TABLE claims ADD COLUMN spawn_yaw REAL",
            "ALTER TABLE claims ADD COLUMN spawn_pitch REAL"
        };
    }

    @Override
    protected String[] taxColumnMigrations() {
        return new String[]{
            "ALTER TABLE claims ADD COLUMN tax_next_due BIGINT DEFAULT 0",
            "ALTER TABLE claims ADD COLUMN tax_grace_end BIGINT DEFAULT 0",
            "UPDATE claims SET tax_next_due = upkeep_next_due, tax_grace_end = upkeep_grace_end WHERE tax_next_due = 0 AND upkeep_next_due > 0"
        };
    }

    @Override
    protected String claimLogsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "action VARCHAR(50) NOT NULL," +
                "player_name VARCHAR(36) NOT NULL," +
                "details VARCHAR(500)," +
                "timestamp BIGINT NOT NULL" +
                ")";
    }
}
