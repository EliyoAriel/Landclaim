package com.landclaim;

import com.landclaim.claim.ClaimBorder;
import com.landclaim.claim.ClaimManager;
import com.landclaim.command.ClaimCommand;
import com.landclaim.database.DatabaseManager;
import com.landclaim.database.MySQLDatabase;
import com.landclaim.database.SQLiteDatabase;
import com.landclaim.economy.EconomyManager;
import com.landclaim.listener.AdminInputListener;
import com.landclaim.listener.ClaimListener;
import com.landclaim.papi.LandClaimExpansion;
import com.landclaim.util.MessageManager;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class LandClaimPlugin extends JavaPlugin {

    private static LandClaimPlugin instance;
    private DatabaseManager database;
    private ClaimManager claimManager;
    private EconomyManager economyManager;
    private ClaimBorder claimBorder;
    private final Map<UUID, String> pendingInputs = new ConcurrentHashMap<>();
    private final Set<UUID> adminBypassDisabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> homeCooldowns = new ConcurrentHashMap<>();
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        mergeConfigDefaults();

        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!initDatabase()) {
            getLogger().severe("Failed to initialize database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.claimManager = new ClaimManager(this, database);
        this.economyManager = new EconomyManager(this);
        this.claimBorder = new ClaimBorder(this);

        this.messageManager = new MessageManager(this);

        registerCommands();
        registerListeners();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LandClaimExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        long interval = getConfig().getInt("tax.interval-hours", 24) * 3600000L;
        long tickInterval = interval / 50L;
        if (tickInterval < 1200) tickInterval = 1200;
        Bukkit.getScheduler().runTaskTimer(this, () -> claimManager.processTax(), tickInterval, tickInterval);

        getLogger().info("LandClaim v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (claimBorder != null) claimBorder.stopAll();
        if (claimManager != null) claimManager.saveAll();
        if (database != null) database.close();
        getLogger().info("LandClaim disabled.");
    }

    private boolean initDatabase() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        try {
            switch (dbType) {
                case "mysql" -> database = new MySQLDatabase(this);
                default -> database = new SQLiteDatabase(this);
            }
            database.init();
            return true;
        } catch (Exception e) {
            getLogger().severe("Database error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void mergeConfigDefaults() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;
        FileConfiguration existing = YamlConfiguration.loadConfiguration(file);
        InputStream defStream = getResource("config.yml");
        if (defStream == null) return;
        FileConfiguration def = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defStream, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (!def.isConfigurationSection(key) && !existing.contains(key)) {
                existing.set(key, def.get(key));
                changed = true;
            }
        }

        String[] legacyKeys = {
            "upkeep.enabled", "upkeep.interval-hours", "upkeep.grace-days",
            "upkeep.cost-per-tier"
        };
        for (String key : legacyKeys) {
            if (existing.contains(key)) {
                existing.set(key, null);
                changed = true;
            }
        }

        if (changed) {
            try {
                existing.save(file);
                getLogger().info("Updated config.yml with new keys.");
            } catch (Exception e) {
                getLogger().warning("Could not save updated config.yml: " + e.getMessage());
            }
        }
    }

    public void reload() {
        reloadConfig();
        messageManager.reload();
        claimManager.loadAll();
    }

    private boolean setupEconomy() {
        return getServer().getPluginManager().getPlugin("Vault") != null;
    }

    private void registerCommands() {
        PluginCommand claim = getCommand("claim");
        if (claim != null) {
            ClaimCommand executor = new ClaimCommand(this);
            claim.setExecutor(executor);
            claim.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ClaimListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminInputListener(this), this);
    }

    public void setPendingInput(UUID playerId, String type) {
        if (type == null) pendingInputs.remove(playerId);
        else pendingInputs.put(playerId, type);
    }

    public String getPendingInput(UUID playerId) {
        return pendingInputs.get(playerId);
    }

    public boolean isAdminBypassDisabled(UUID playerId) {
        return adminBypassDisabled.contains(playerId);
    }

    public boolean toggleAdminBypass(UUID playerId) {
        if (adminBypassDisabled.contains(playerId)) {
            adminBypassDisabled.remove(playerId);
            return false;
        } else {
            adminBypassDisabled.add(playerId);
            return true;
        }
    }

    public Map<UUID, Long> getHomeCooldowns() {
        return homeCooldowns;
    }

    public static LandClaimPlugin getInstance() {
        return instance;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ClaimBorder getClaimBorder() {
        return claimBorder;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public MessageManager getMsg() {
        return messageManager;
    }
}
