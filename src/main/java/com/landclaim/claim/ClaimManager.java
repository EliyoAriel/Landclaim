package com.landclaim.claim;

import com.landclaim.LandClaimPlugin;
import com.landclaim.api.ClaimCreateEvent;
import com.landclaim.api.ClaimDeleteEvent;
import com.landclaim.api.ClaimTransferEvent;
import com.landclaim.database.DatabaseManager;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {

    private final LandClaimPlugin plugin;
    private final DatabaseManager database;
    private final Map<UUID, Claim> claims = new ConcurrentHashMap<>();
    private final Set<UUID> globalTrust = ConcurrentHashMap.newKeySet();
    private final Set<UUID> notifyDisabled = ConcurrentHashMap.newKeySet();

    public ClaimManager(LandClaimPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        loadAll();
    }

    public Claim createClaim(UUID owner, Location location) {
        int defaultRadius = plugin.getConfig().getInt("default-radius", 5);
        if (getClaim(owner) != null) return null;
        if (isBlacklisted(location)) return null;

        org.bukkit.entity.Player player = plugin.getServer().getPlayer(owner);

        int startTier = plugin.getConfig().getInt("start-tier", 1);
        if (player != null) {
            int startPermTier = getPermissionStartTier(player);
            if (startPermTier > startTier) startTier = startPermTier;
            if (plugin.getConfig().getBoolean("use-permission-start-tier", false)) {
                int maxPermTier = getPermissionMaxTier(player);
                if (maxPermTier > startTier) startTier = maxPermTier;
            }
        }
        if (player != null) {
            int maxTier = getEffectiveMaxTier(player);
            if (startTier > maxTier) startTier = maxTier;
        }
        if (startTier < 1) startTier = 1;
        int startRadius = startTier > 1
                ? plugin.getConfig().getInt("tiers." + startTier + ".radius", defaultRadius)
                : defaultRadius;

        Claim claim = new Claim(owner, location.getWorld(),
                location.getBlockX(), location.getBlockZ(),
                startRadius, startTier);

        if (collidesWithAny(claim)) return null;

        if (player != null) {
            ClaimCreateEvent event = new ClaimCreateEvent(player, claim);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return null;
        }

        claims.put(owner, claim);
        database.saveClaim(claim);
        database.insertLog(owner.toString(), "CREATE", plugin.getServer().getOfflinePlayer(owner).getName(), "Claim created");
        return claim;
    }

    public void removeClaim(UUID owner) {
        Claim claim = claims.get(owner);
        if (claim != null) {
            ClaimDeleteEvent event = new ClaimDeleteEvent(owner, claim, ClaimDeleteEvent.DeleteReason.ABANDON);
            plugin.getServer().getPluginManager().callEvent(event);
            database.insertLog(owner.toString(), "ABANDON", plugin.getServer().getOfflinePlayer(owner).getName(), "Claim abandoned");
        }
        claims.remove(owner);
        database.removeClaim(owner);
        plugin.getClaimBorder().remove(owner);
    }

    public void transferClaim(UUID oldOwner, UUID newOwner) {
        Claim claim = claims.get(oldOwner);
        if (claim == null) return;

        if (claims.containsKey(newOwner)) return;

        org.bukkit.entity.Player player = plugin.getServer().getPlayer(oldOwner);
        if (player != null) {
            ClaimTransferEvent event = new ClaimTransferEvent(player, oldOwner, newOwner, claim);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
        }

        Claim transferred = new Claim(newOwner, claim.getWorld(), claim.getCenterX(), claim.getCenterZ(),
                claim.getRadius(), claim.getTier());
        for (ClaimFlag flag : ClaimFlag.values()) {
            transferred.setFlag(flag, claim.getFlags().getOrDefault(flag, flag.getDefaultValue()));
        }

        database.transferClaim(oldOwner, newOwner);
        database.insertLog(newOwner.toString(), "TRANSFER",
                plugin.getServer().getOfflinePlayer(oldOwner).getName(),
                "Transferred to " + plugin.getServer().getOfflinePlayer(newOwner).getName());

        claims.remove(oldOwner);
        claims.put(newOwner, transferred);
    }

    public Claim getClaim(UUID owner) {
        return claims.get(owner);
    }

    public Claim getClaimAt(Location location) {
        for (Claim claim : claims.values()) {
            if (claim.contains(location)) return claim;
        }
        return null;
    }

    public int getPermissionMaxTier(org.bukkit.entity.Player player) {
        int maxTier = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
        int permTier = 0;
        for (int t = maxTier; t >= 1; t--) {
            if (player.hasPermission("landclaim.tier." + t)) {
                permTier = t;
                break;
            }
        }
        return permTier;
    }

    public int getPermissionStartTier(org.bukkit.entity.Player player) {
        int maxTier = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
        int permTier = 0;
        for (int t = maxTier; t >= 1; t--) {
            if (player.hasPermission("landclaim.starttier." + t)) {
                permTier = t;
                break;
            }
        }
        return permTier;
    }

    private int getEffectiveMaxTier(org.bukkit.entity.Player player) {
        int configMax = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
        int permMax = getPermissionMaxTier(player);
        // permMax of 1 is typically the default permission everyone has, so don't cap
        return permMax > 1 ? Math.min(permMax, configMax) : configMax;
    }

    public boolean canUpgrade(UUID owner) {
        Claim claim = claims.get(owner);
        if (claim == null) return false;
        int maxTier = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
        return claim.getTier() < maxTier;
    }

    public boolean canUpgrade(org.bukkit.entity.Player player) {
        Claim claim = claims.get(player.getUniqueId());
        if (claim == null) return false;
        return claim.getTier() < getEffectiveMaxTier(player);
    }

    public int getUpgradeCost(UUID owner) {
        Claim claim = claims.get(owner);
        if (claim == null) return -1;
        int nextTier = claim.getTier() + 1;
        return plugin.getConfig().getInt("tiers." + nextTier + ".price", 0);
    }

    public int getUpgradeCost(org.bukkit.entity.Player player) {
        return getUpgradeCost(player.getUniqueId());
    }

    public boolean upgrade(org.bukkit.entity.Player player) {
        Claim claim = claims.get(player.getUniqueId());
        if (claim == null || !canUpgrade(player)) return false;

        int effectiveMax = getEffectiveMaxTier(player);
        int nextTier = claim.getTier() + 1;
        if (nextTier > effectiveMax) return false;

        int newRadius = plugin.getConfig().getInt("tiers." + nextTier + ".radius", claim.getRadius() + 25);
        int oldRadius = claim.getRadius();
        claim.setRadius(newRadius);

        if (collidesWithAny(claim)) {
            claim.setRadius(oldRadius);
            return false;
        }

        claim.setTier(nextTier);
        database.saveClaim(claim);
        UUID owner = player.getUniqueId();
        database.insertLog(owner.toString(), "UPGRADE",
                plugin.getServer().getOfflinePlayer(owner).getName(),
                "Upgraded to tier " + nextTier);
        return true;
    }

    public boolean collidesWithAny(Claim target) {
        for (Claim other : claims.values()) {
            if (other.getOwner().equals(target.getOwner())) continue;
            if (target.overlaps(other)) return true;
        }
        return false;
    }

    public boolean isBlacklisted(Location location) {
        String worldName = location.getWorld().getName();
        List<String> worlds = plugin.getConfig().getStringList("blacklist.worlds");
        if (worlds.contains(worldName)) return true;

        List<Map<?, ?>> areas = plugin.getConfig().getMapList("blacklist.areas");
        for (Map<?, ?> area : areas) {
            if (!area.get("world").equals(worldName)) continue;
            int x1 = Math.min((int) area.get("x1"), (int) area.get("x2"));
            int z1 = Math.min((int) area.get("z1"), (int) area.get("z2"));
            int x2 = Math.max((int) area.get("x1"), (int) area.get("x2"));
            int z2 = Math.max((int) area.get("z1"), (int) area.get("z2"));
            int bx = location.getBlockX();
            int bz = location.getBlockZ();
            if (bx >= x1 && bx <= x2 && bz >= z1 && bz <= z2) return true;
        }
        return false;
    }

    public Collection<Claim> getAllClaims() {
        return claims.values();
    }

    public List<String> getBlacklistWorlds() {
        return plugin.getConfig().getStringList("blacklist.worlds");
    }

    public void addBlacklistWorld(String world) {
        List<String> worlds = getBlacklistWorlds();
        if (!worlds.contains(world)) {
            worlds.add(world);
            plugin.getConfig().set("blacklist.worlds", worlds);
            plugin.saveConfig();
        }
    }

    public void removeBlacklistWorld(String world) {
        List<String> worlds = getBlacklistWorlds();
        worlds.remove(world);
        plugin.getConfig().set("blacklist.worlds", worlds);
        plugin.saveConfig();
    }

    public List<Map<?, ?>> getBlacklistAreas() {
        return plugin.getConfig().getMapList("blacklist.areas");
    }

    public void addBlacklistArea(String world, int x1, int z1, int x2, int z2) {
        List<Map<String, Object>> areas = new ArrayList<>();
        for (Map<?, ?> a : getBlacklistAreas()) {
            Map<String, Object> m = new HashMap<>();
            m.putAll((Map<String, Object>) a);
            areas.add(m);
        }
        Map<String, Object> area = new HashMap<>();
        area.put("world", world);
        area.put("x1", Math.min(x1, x2));
        area.put("z1", Math.min(z1, z2));
        area.put("x2", Math.max(x1, x2));
        area.put("z2", Math.max(z1, z2));
        areas.add(area);
        plugin.getConfig().set("blacklist.areas", areas);
        plugin.saveConfig();
    }

    public void removeBlacklistArea(int index) {
        List<Map<String, Object>> areas = new ArrayList<>();
        for (Map<?, ?> a : getBlacklistAreas()) {
            Map<String, Object> m = new HashMap<>();
            m.putAll((Map<String, Object>) a);
            areas.add(m);
        }
        if (index >= 0 && index < areas.size()) {
            areas.remove(index);
            plugin.getConfig().set("blacklist.areas", areas);
            plugin.saveConfig();
        }
    }

    public void saveAll() {
        for (Claim claim : claims.values()) {
            database.saveClaim(claim);
        }
        database.saveGlobalTrust(globalTrust);
    }

    public void loadAll() {
        Map<UUID, Claim> loaded = database.loadAllClaims();
        claims.clear();
        claims.putAll(loaded);
        globalTrust.clear();
        globalTrust.addAll(database.loadGlobalTrust());
        notifyDisabled.clear();
        notifyDisabled.addAll(database.loadNotifyDisabled());
    }

    public boolean isGloballyTrusted(UUID player) {
        return globalTrust.contains(player);
    }

    public void addGlobalTrust(UUID player) {
        globalTrust.add(player);
        database.addGlobalTrust(player);
    }

    public void removeGlobalTrust(UUID player) {
        globalTrust.remove(player);
        database.removeGlobalTrust(player);
    }

    public boolean isNotifyEnabled(UUID player) {
        return !notifyDisabled.contains(player);
    }

    public boolean toggleNotify(UUID player) {
        if (notifyDisabled.contains(player)) {
            notifyDisabled.remove(player);
            database.saveNotifyDisabled(player, false);
            return true;
        } else {
            notifyDisabled.add(player);
            database.saveNotifyDisabled(player, true);
            return false;
        }
    }

    public Set<UUID> getGlobalTrustedPlayers() {
        return Collections.unmodifiableSet(globalTrust);
    }

    public void logClaimEvent(String ownerUuid, String action, String player, String details) {
        database.insertLog(ownerUuid, action, player, details);
    }

    public List<Map<String, Object>> getClaimLogs(String ownerUuid, int limit) {
        return database.loadLogs(ownerUuid, limit);
    }

    public void processTax() {
        if (!plugin.getConfig().getBoolean("tax.enabled", false)) return;
        List<Integer> costs = plugin.getConfig().getIntegerList("tax.cost-per-tier");
        int graceDays = plugin.getConfig().getInt("tax.grace-days", 3);
        int defaultRadius = plugin.getConfig().getInt("default-radius", 5);
        long now = System.currentTimeMillis();

        for (Claim claim : claims.values()) {
            org.bukkit.OfflinePlayer off = plugin.getServer().getOfflinePlayer(claim.getOwner());
            org.bukkit.entity.Player online = off.getPlayer();

            if (online != null && online.hasPermission("landclaim.tax.exempt")) continue;

            int tier = claim.getTier();
            int baseCost = tier <= costs.size() ? costs.get(tier - 1) : 0;
            if (baseCost <= 0) continue;

            int cost = (int) Math.round((double) baseCost * claim.getRadius() / defaultRadius);
            if (cost <= 0) continue;

            if (claim.getTaxNextDue() == 0) {
                long interval = plugin.getConfig().getInt("tax.interval-hours", 24) * 3600000L;
                claim.setTaxNextDue(now + interval);
                continue;
            }

            if (now < claim.getTaxNextDue()) continue;

            if (claim.getTaxGraceEnd() > 0) {
                if (now > claim.getTaxGraceEnd()) {
                    removeClaim(claim.getOwner());
                    database.insertLog(claim.getOwner().toString(), "ABANDON_TAX",
                        plugin.getServer().getOfflinePlayer(claim.getOwner()).getName(),
                        "Claim abandoned due to unpaid tax");
                    if (online != null) {
                        online.sendMessage("§cYour claim was abandoned due to unpaid taxes!");
                    }
                    continue;
                }
            }

            if (plugin.getEconomyManager().hasBalance(off, cost)) {
                plugin.getEconomyManager().withdraw(off, cost);
                long interval = plugin.getConfig().getInt("tax.interval-hours", 24) * 3600000L;
                claim.setTaxNextDue(now + interval);
                claim.setTaxGraceEnd(0);
                database.insertLog(claim.getOwner().toString(), "TAX_PAID",
                    plugin.getServer().getOfflinePlayer(claim.getOwner()).getName(),
                    "Paid $" + cost + " tax");
                if (online != null) {
                    online.sendMessage("§e[$" + cost + " tax deducted from your balance]");
                }
            } else {
                long graceMs = graceDays * 86400000L;
                claim.setTaxGraceEnd(now + graceMs);
                database.insertLog(claim.getOwner().toString(), "TAX_GRACE",
                    plugin.getServer().getOfflinePlayer(claim.getOwner()).getName(),
                    "Tax failed, grace until " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(now + graceMs)));
                if (online != null) {
                    online.sendMessage("§c§l⚠ Insufficient funds for claim tax ($" + cost + ")!");
                    online.sendMessage("§7Your claim enters grace for §f" + graceDays + " §7days, then will be abandoned.");
                }
            }
        }
        saveAll();
    }
}
