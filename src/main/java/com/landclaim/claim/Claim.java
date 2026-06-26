package com.landclaim.claim;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Claim {
    private final UUID owner;
    private final World world;
    private final int centerX;
    private final int centerZ;
    private int radius;
    private int tier;
    private String name;
    private final Map<UUID, EnumSet<ClaimPermission>> allowedPlayers = new HashMap<>();
    private final Map<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);
    private final Set<UUID> bannedPlayers = ConcurrentHashMap.newKeySet();
    private Location spawnLocation;
    private long taxNextDue;
    private long taxGraceEnd;

    public Claim(UUID owner, World world, int centerX, int centerZ, int radius, int tier) {
        this.owner = owner;
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.tier = tier;
        for (ClaimFlag f : ClaimFlag.values()) {
            flags.put(f, f.getDefaultValue());
        }
    }

    public boolean contains(Location location) {
        if (!location.getWorld().equals(world)) return false;
        int dx = Math.abs(location.getBlockX() - centerX);
        int dz = Math.abs(location.getBlockZ() - centerZ);
        return dx <= radius && dz <= radius;
    }

    public boolean overlaps(Claim other) {
        if (!other.world.equals(world)) return false;
        int dx = Math.abs(this.centerX - other.centerX);
        int dz = Math.abs(this.centerZ - other.centerZ);
        int totalRadius = this.radius + other.radius;
        return dx <= totalRadius && dz <= totalRadius;
    }

    public boolean isPlayerAllowed(UUID uuid, ClaimPermission permission) {
        if (owner.equals(uuid)) return true;
        if (bannedPlayers.contains(uuid)) return false;
        EnumSet<ClaimPermission> perms = allowedPlayers.get(uuid);
        return perms != null && perms.contains(permission);
    }

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.contains(uuid);
    }

    public void ban(UUID uuid) {
        bannedPlayers.add(uuid);
        allowedPlayers.remove(uuid);
    }

    public void unban(UUID uuid) {
        bannedPlayers.remove(uuid);
    }

    public Set<UUID> getBannedPlayers() {
        return bannedPlayers;
    }

    public void addAllowed(UUID uuid) {
        allowedPlayers.put(uuid, EnumSet.allOf(ClaimPermission.class));
    }

    public void addAllowed(UUID uuid, Set<ClaimPermission> permissions) {
        allowedPlayers.put(uuid, permissions.isEmpty()
                ? EnumSet.allOf(ClaimPermission.class)
                : EnumSet.copyOf(permissions));
    }

    public void setPermissions(UUID uuid, Set<ClaimPermission> permissions) {
        if (allowedPlayers.containsKey(uuid)) {
            allowedPlayers.put(uuid, EnumSet.copyOf(permissions));
        }
    }

    public void removeAllowed(UUID uuid) {
        allowedPlayers.remove(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return allowedPlayers.containsKey(uuid);
    }

    public EnumSet<ClaimPermission> getPermissions(UUID uuid) {
        return allowedPlayers.get(uuid);
    }

    public Map<UUID, EnumSet<ClaimPermission>> getAllowedPlayers() {
        return allowedPlayers;
    }

    public boolean getFlag(ClaimFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultValue());
    }

    public void setFlag(ClaimFlag flag, boolean value) {
        flags.put(flag, value);
    }

    public Map<ClaimFlag, Boolean> getFlags() {
        return flags;
    }

    public UUID getOwner() { return owner; }
    public World getWorld() { return world; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public int getRadius() { return radius; }
    public int getTier() { return tier; }
    public String getName() { return name; }

    public void setRadius(int radius) { this.radius = radius; }
    public void setTier(int tier) { this.tier = tier; }
    public void setName(String name) { this.name = name; }

    @Nullable
    public Location getSpawnLocation() { return spawnLocation; }

    public void setSpawnLocation(@Nullable Location loc) { this.spawnLocation = loc; }

    public boolean hasCustomSpawn() { return spawnLocation != null; }

    public long getTaxNextDue() { return taxNextDue; }
    public void setTaxNextDue(long taxNextDue) { this.taxNextDue = taxNextDue; }
    public long getTaxGraceEnd() { return taxGraceEnd; }
    public void setTaxGraceEnd(long taxGraceEnd) { this.taxGraceEnd = taxGraceEnd; }
    public boolean isTaxDelinquent() { return taxGraceEnd > 0 && System.currentTimeMillis() > taxGraceEnd; }
}
