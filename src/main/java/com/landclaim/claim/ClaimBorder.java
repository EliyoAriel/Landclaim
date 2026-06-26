package com.landclaim.claim;

import com.landclaim.LandClaimPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimBorder {

    private final LandClaimPlugin plugin;
    private final Map<UUID, BukkitTask> activeBorders = new HashMap<>();

    public ClaimBorder(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggle(Player player) {
        UUID id = player.getUniqueId();
        if (activeBorders.containsKey(id)) {
            stop(player);
            player.sendMessage(plugin.getMsg().raw("gui.border.disabled"));
        } else {
            Claim claim = plugin.getClaimManager().getClaim(id);
            if (claim == null) {
                player.sendMessage(plugin.getMsg().raw("gui.border.no_claim"));
                return;
            }
            start(player, claim);
            player.sendMessage(plugin.getMsg().raw("gui.border.enabled"));
        }
    }

    public void start(Player player, Claim claim) {
        stop(player);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || plugin.getClaimManager().getClaim(player.getUniqueId()) == null) {
                stop(player);
                return;
            }
            showBorder(player, claim);
        }, 0L, 20L);
        activeBorders.put(player.getUniqueId(), task);
    }

    public void stop(Player player) {
        BukkitTask task = activeBorders.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void stopAll() {
        activeBorders.values().forEach(BukkitTask::cancel);
        activeBorders.clear();
    }

    private void showBorder(Player player, Claim claim) {
        World world = claim.getWorld();
        if (!player.getWorld().equals(world)) return;

        int r = claim.getRadius();
        int cx = claim.getCenterX();
        int cz = claim.getCenterZ();
        double y = player.getLocation().getY();

        int step = Math.max(1, r / 5);
        for (int i = -r; i <= r; i += step) {
            spawnParticle(player, world, cx + i, y, cz - r);
            spawnParticle(player, world, cx + i, y, cz + r);
            spawnParticle(player, world, cx - r, y, cz + i);
            spawnParticle(player, world, cx + r, y, cz + i);
        }
    }

    private void spawnParticle(Player player, World world, double x, double y, double z) {
        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1));
    }

    public boolean isShowing(UUID playerId) {
        return activeBorders.containsKey(playerId);
    }

    public void remove(UUID playerId) {
        stop(plugin.getServer().getPlayer(playerId));
    }
}
