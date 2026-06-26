package com.landclaim.database;

import com.landclaim.LandClaimPlugin;
import com.zaxxer.hikari.HikariConfig;

public class MySQLDatabase extends DatabaseManager {

    public MySQLDatabase(LandClaimPlugin plugin) {
        super(plugin);
    }

    @Override
    public void init() {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "landclaim");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");
        int poolSize = plugin.getConfig().getInt("database.mysql.pool-size", 10);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("LandClaim-MySQL");

        dataSource = new com.zaxxer.hikari.HikariDataSource(config);
        initTables();
        plugin.getLogger().info("Database: MySQL (" + host + ":" + port + "/" + database + ")");
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
                "spawn_yaw FLOAT," +
                "spawn_pitch FLOAT," +
                "tax_next_due BIGINT DEFAULT 0," +
                "tax_grace_end BIGINT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String permissionsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_permissions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "trusted_uuid VARCHAR(36) NOT NULL," +
                "permission VARCHAR(20) NOT NULL," +
                "UNIQUE KEY uk_perm (owner_uuid, trusted_uuid, permission)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String flagsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_flags (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "flag_name VARCHAR(50) NOT NULL," +
                "flag_value BOOLEAN NOT NULL DEFAULT TRUE," +
                "UNIQUE KEY uk_flag (owner_uuid, flag_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String globalTrustDDL() {
        return "CREATE TABLE IF NOT EXISTS global_trust (" +
                "player_uuid VARCHAR(36) PRIMARY KEY" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String claimNamesTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_names (" +
                "owner_uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(255) NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String claimBansTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_bans (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "banned_uuid VARCHAR(36) NOT NULL," +
                "UNIQUE KEY uk_ban (owner_uuid, banned_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String playerSettingsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS player_settings (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "setting_key VARCHAR(50) NOT NULL," +
                "setting_value VARCHAR(255) NOT NULL," +
                "UNIQUE KEY uk_setting (player_uuid, setting_key)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String[] spawnColumnMigrations() {
        return new String[]{
            "ALTER TABLE claims ADD COLUMN spawn_x INT",
            "ALTER TABLE claims ADD COLUMN spawn_y INT",
            "ALTER TABLE claims ADD COLUMN spawn_z INT",
            "ALTER TABLE claims ADD COLUMN spawn_yaw FLOAT",
            "ALTER TABLE claims ADD COLUMN spawn_pitch FLOAT"
        };
    }

    @Override
    protected String[] taxColumnMigrations() {
        return new String[]{
            "ALTER TABLE claims ADD COLUMN tax_next_due BIGINT DEFAULT 0",
            "ALTER TABLE claims ADD COLUMN tax_grace_end BIGINT DEFAULT 0",
            "UPDATE claims SET tax_next_due = upkeep_next_due, tax_grace_end = upkeep_grace_end WHERE upkeep_next_due > 0"
        };
    }

    @Override
    protected String claimLogsTableDDL() {
        return "CREATE TABLE IF NOT EXISTS claim_logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "action VARCHAR(50) NOT NULL," +
                "player_name VARCHAR(36) NOT NULL," +
                "details VARCHAR(500)," +
                "timestamp BIGINT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }
}
