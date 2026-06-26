package com.landclaim.listener;

import com.landclaim.LandClaimPlugin;
import com.landclaim.api.ClaimProtectionCheckEvent;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimFlag;
import com.landclaim.claim.ClaimPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimListener implements Listener {

    private final LandClaimPlugin plugin;
    private final Map<UUID, Claim> lastClaim = new ConcurrentHashMap<>();

    public ClaimListener(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canBypass(Player player) {
        if (player.hasPermission("landclaim.bypass.protection")) return true;
        return player.hasPermission("landclaim.admin") && !plugin.isAdminBypassDisabled(player.getUniqueId());
    }

    private boolean isGloballyTrusted(Player player) {
        return plugin.getClaimManager().isGloballyTrusted(player.getUniqueId());
    }

    private boolean isOwnerOnline(Claim claim) {
        return Bukkit.getPlayer(claim.getOwner()) != null;
    }

    private boolean checkBanned(Player player, Claim claim) {
        if (claim.isBanned(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("protection.banned"));
            return true;
        }
        return false;
    }

    private boolean fireProtectionCheck(Player player, Claim claim, ClaimPermission perm,
                                         ClaimProtectionCheckEvent.ActionType action) {
        ClaimProtectionCheckEvent event = new ClaimProtectionCheckEvent(player, claim, perm, action);
        plugin.getServer().getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-break", true)) return;
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (isGloballyTrusted(player)) return;
        if (isOwnerOnline(claim) && checkBanned(player, claim)) { event.setCancelled(true); return; }
        if (plugin.getConfig().getBoolean("protection.offline-extra", false) && !isOwnerOnline(claim)) {
            event.setCancelled(true);
            return;
        }
        if (fireProtectionCheck(player, claim, ClaimPermission.BUILD, ClaimProtectionCheckEvent.ActionType.BLOCK_BREAK)) return;
        if (!claim.isPlayerAllowed(player.getUniqueId(), ClaimPermission.BUILD)) {
            event.setCancelled(true);
            String ownerName = plugin.getServer().getOfflinePlayer(claim.getOwner()).getName();
            player.sendMessage(plugin.getMsg().text("protection.denied_build", Map.of("owner", ownerName)));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-place", true)) return;
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (isGloballyTrusted(player)) return;
        if (isOwnerOnline(claim) && checkBanned(player, claim)) { event.setCancelled(true); return; }
        if (plugin.getConfig().getBoolean("protection.offline-extra", false) && !isOwnerOnline(claim)) {
            event.setCancelled(true);
            return;
        }
        if (fireProtectionCheck(player, claim, ClaimPermission.BUILD, ClaimProtectionCheckEvent.ActionType.BLOCK_PLACE)) return;
        if (!claim.isPlayerAllowed(player.getUniqueId(), ClaimPermission.BUILD)) {
            event.setCancelled(true);
            String ownerName = plugin.getServer().getOfflinePlayer(claim.getOwner()).getName();
            player.sendMessage(plugin.getMsg().text("protection.denied_build", Map.of("owner", ownerName)));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("protection.interact", true)) return;
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (canBypass(player)) return;

        String typeName = event.getClickedBlock().getType().name();
        boolean isContainer = switch (typeName) {
            case "CHEST", "TRAPPED_CHEST", "BARREL", "FURNACE", "BLAST_FURNACE",
                 "SMOKER", "BREWING_STAND", "HOPPER", "DISPENSER", "DROPPER",
                 "SHULKER_BOX", "CRAFTING_TABLE", "ANVIL", "GRINDSTONE",
                 "STONECUTTER", "CARTOGRAPHY_TABLE", "LOOM", "SMITHING_TABLE",
                 "COMPOSTER" -> true;
            default -> false;
        };

        boolean isSwitch = plugin.getConfig().getBoolean("protection.chest-access", true)
                && (typeName.endsWith("_DOOR") || typeName.endsWith("_FENCE_GATE")
                || typeName.equals("LEVER") || typeName.endsWith("_BUTTON")
                || typeName.endsWith("_PRESSURE_PLATE"));

        if (!isContainer && !isSwitch) return;

        ClaimPermission required = isContainer ? ClaimPermission.CHEST : ClaimPermission.INTERACT;
        ClaimProtectionCheckEvent.ActionType actionType = isContainer
                ? ClaimProtectionCheckEvent.ActionType.INTERACT_CONTAINER
                : ClaimProtectionCheckEvent.ActionType.INTERACT_SWITCH;

        Claim claim = plugin.getClaimManager().getClaimAt(event.getClickedBlock().getLocation());
        if (claim == null) return;
        if (isGloballyTrusted(player)) return;
        if (isOwnerOnline(claim) && checkBanned(player, claim)) { event.setCancelled(true); return; }
        if (plugin.getConfig().getBoolean("protection.offline-extra", false) && !isOwnerOnline(claim)) {
            event.setCancelled(true);
            return;
        }
        if (fireProtectionCheck(player, claim, required, actionType)) return;
        if (!claim.isPlayerAllowed(player.getUniqueId(), required)) {
            event.setCancelled(true);
            String ownerName = plugin.getServer().getOfflinePlayer(claim.getOwner()).getName();
            player.sendMessage(plugin.getMsg().text(isContainer ? "protection.denied_chest" : "protection.denied_interact", Map.of("owner", ownerName)));
        }
    }

    @EventHandler
    public void onPVP(EntityDamageByEntityEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;
        if (!(event.getDamager() instanceof Player)) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getEntity().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.PVP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireSpread(BlockSpreadEvent event) {
        if (!event.getSource().getType().name().contains("FIRE")) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if (event.getEntity() == null) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.EXPLOSIONS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobGrief(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.ENDERMAN
                && event.getEntityType() != EntityType.RAVAGER) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim.getType() == EntityType.PLAYER) return;

        Claim claim = plugin.getClaimManager().getClaimAt(victim.getLocation());
        if (claim == null) return;

        boolean isVehicle = victim instanceof Vehicle
                || victim instanceof Minecart
                || victim instanceof Boat;
        if (isVehicle && !claim.getFlag(ClaimFlag.VEHICLE_DAMAGE)) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getDamager() instanceof Player)) return;

        boolean isAnimal = victim instanceof Animals || victim instanceof Tameable;
        boolean isMob = victim instanceof Monster;

        if (isAnimal && !claim.getFlag(ClaimFlag.ANIMAL_DAMAGE)) {
            event.setCancelled(true);
        } else if (isMob && !claim.getFlag(ClaimFlag.MOB_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCropTrample(MoistureChangeEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.CROP_TRAMPLE)) {
            event.setCancelled(true);
        }
    }

    private static final Set<Material> FARMLAND_BLOCKS = EnumSet.of(
            Material.FARMLAND, Material.SOUL_SAND, Material.SOUL_SOIL);

    @EventHandler
    public void onFarmlandTrample(EntityBlockFormEvent event) {
        if (!FARMLAND_BLOCKS.contains(event.getBlock().getType())) return;
        if (event.getEntity() instanceof Player) return;
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (!claim.getFlag(ClaimFlag.CROP_TRAMPLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        org.bukkit.block.Block piston = event.getBlock();
        Claim pistonClaim = plugin.getClaimManager().getClaimAt(piston.getLocation());
        if (pistonClaim != null && pistonClaim.getFlag(ClaimFlag.PISTON_PROTECTION)) {
            event.setCancelled(true);
            return;
        }
        org.bukkit.block.BlockFace dir = event.getDirection();
        for (org.bukkit.block.Block block : event.getBlocks()) {
            org.bukkit.Location dest = block.getLocation().add(dir.getDirection());
            Claim destClaim = plugin.getClaimManager().getClaimAt(dest);
            if (destClaim != null && !destClaim.equals(pistonClaim)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        org.bukkit.block.Block piston = event.getBlock();
        Claim pistonClaim = plugin.getClaimManager().getClaimAt(piston.getLocation());
        if (pistonClaim != null && pistonClaim.getFlag(ClaimFlag.PISTON_PROTECTION)) {
            event.setCancelled(true);
            return;
        }
        if (event.isSticky()) {
            org.bukkit.block.BlockFace dir = event.getDirection();
            for (org.bukkit.block.Block block : event.getBlocks()) {
                org.bukkit.Location dest = block.getLocation().subtract(dir.getDirection());
                Claim destClaim = plugin.getClaimManager().getClaimAt(dest);
                if (destClaim != null && !destClaim.equals(pistonClaim)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Claim fromClaim = plugin.getClaimManager().getClaimAt(event.getFrom());
        Claim toClaim = plugin.getClaimManager().getClaimAt(event.getTo());

        if (fromClaim == toClaim) return;

        if (toClaim != null) {
            String ownerName = plugin.getServer().getOfflinePlayer(toClaim.getOwner()).getName();
            String display = toClaim.getName() != null ? toClaim.getName() : ownerName;

            if (plugin.getClaimManager().isNotifyEnabled(player.getUniqueId())) {
                String msg = plugin.getMsg().rawNoPrefix("enter.message", Map.of("claim", display, "owner", ownerName));
                if (!msg.isEmpty()) {
                    player.sendMessage(Component.text(msg));
                }

                player.showTitle(plugin.getMsg().title("enter.title", "enter.subtitle", Map.of("claim", display, "owner", ownerName)));
            }

            lastClaim.put(player.getUniqueId(), toClaim);
        } else {
            lastClaim.remove(player.getUniqueId());
        }
    }
}
