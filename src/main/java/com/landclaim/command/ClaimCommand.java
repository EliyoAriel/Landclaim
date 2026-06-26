package com.landclaim.command;

import com.landclaim.LandClaimPlugin;
import com.landclaim.api.ClaimBanEvent;
import com.landclaim.api.ClaimTrustEvent;
import com.landclaim.api.ClaimUnbanEvent;
import com.landclaim.api.ClaimUntrustEvent;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimFlag;
import com.landclaim.claim.ClaimManager;
import com.landclaim.claim.ClaimPermission;
import com.landclaim.economy.EconomyManager;
import com.landclaim.gui.ClaimGUI;
import com.landclaim.gui.admin.AdminGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final LandClaimPlugin plugin;
    private final ClaimManager claimManager;
    private final EconomyManager economyManager;
    private final Set<UUID> abandonConfirm = new HashSet<>();
    private final Map<UUID, UUID> transferConfirmTargets = new HashMap<>();
    private final Map<UUID, UUID> banConfirmTargets = new HashMap<>();

    public ClaimCommand(LandClaimPlugin plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (claimManager.getClaim(player.getUniqueId()) == null) {
                createClaim(player);
            } else {
                openGUI(player);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> openGUI(player);
            case "upgrade" -> upgradeClaim(player);
            case "info" -> showInfo(player);
            case "border" -> plugin.getClaimBorder().toggle(player);
            case "trust" -> trustPlayer(player, args);
            case "untrust" -> untrustPlayer(player, args.length > 1 ? args[1] : null);
            case "abandon" -> abandonClaim(player, args);
            case "home" -> teleportHome(player);
            case "admin" -> handleAdmin(player, args);
            case "name" -> setName(player, args);
            case "transfer" -> transferClaim(player, args);
            case "ban" -> banPlayer(player, args);
            case "unban" -> unbanPlayer(player, args);
            case "setspawn" -> setSpawn(player);
            case "top" -> showTop(player);
            case "logs" -> showLogs(player);
            case "notify" -> toggleNotify(player);
            default -> player.sendMessage(Component.text("Usage: /claim [gui|upgrade|info|border|trust|untrust|abandon|home|admin|name|transfer|ban|unban|setspawn|top|logs|notify]", NamedTextColor.RED));
        }
        return true;
    }

    private void openGUI(Player player) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.info.no_claim"));
            return;
        }
        new ClaimGUI(plugin, player).open();
    }

    private void createClaim(Player player) {
        if (claimManager.isBlacklisted(player.getLocation())) {
            player.sendMessage(plugin.getMsg().text("claim.create.fail.blacklisted"));
            return;
        }
        Claim existing = claimManager.getClaimAt(player.getLocation());
        if (existing != null) {
            player.sendMessage(plugin.getMsg().text("claim.create.fail.overlapped"));
            return;
        }
        Claim claim = claimManager.createClaim(player.getUniqueId(), player.getLocation());
        if (claim == null) {
            if (claimManager.isBlacklisted(player.getLocation())) {
                player.sendMessage(plugin.getMsg().text("claim.create.fail.blacklisted"));
            } else if (claimManager.collidesWithAny(
                    new Claim(player.getUniqueId(), player.getWorld(),
                            player.getLocation().getBlockX(), player.getLocation().getBlockZ(),
                            plugin.getConfig().getInt("default-radius", 5), 1))) {
                player.sendMessage(plugin.getMsg().text("claim.create.fail.collision"));
            } else {
                player.sendMessage(plugin.getMsg().text("claim.create.fail.exists"));
            }
            return;
        }
        player.sendMessage(plugin.getMsg().text("claim.create.success", Map.of("radius", String.valueOf(claim.getRadius()))));
        player.sendMessage(plugin.getMsg().text("claim.create.tip"));
    }

    private void upgradeClaim(Player player) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.upgrade.no_claim"));
            return;
        }
        if (!claimManager.canUpgrade(player)) {
            player.sendMessage(plugin.getMsg().text("claim.upgrade.max_tier"));
            return;
        }
        int cost = claimManager.getUpgradeCost(player);
        if (!economyManager.hasBalance(player, cost)) {
            player.sendMessage(plugin.getMsg().text("claim.upgrade.insufficient_funds", Map.of(
                    "cost", String.valueOf(cost),
                    "balance", String.valueOf(economyManager.getBalance(player)))));
            return;
        }
        if (!claimManager.upgrade(player)) {
            if (claimManager.collidesWithAny(claim)) {
                player.sendMessage(plugin.getMsg().text("claim.upgrade.collision"));
            }
            return;
        }
        economyManager.withdraw(player, cost);
        player.sendMessage(plugin.getMsg().text("claim.upgrade.success", Map.of(
                "tier", String.valueOf(claim.getTier()),
                "radius", String.valueOf(claim.getRadius()))));
        if (plugin.getClaimBorder().isShowing(player.getUniqueId())) {
            plugin.getClaimBorder().toggle(player);
            plugin.getClaimBorder().toggle(player);
        }
    }

    private void showInfo(Player player) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            Claim current = claimManager.getClaimAt(player.getLocation());
            if (current != null) {
                String ownerName = plugin.getServer().getOfflinePlayer(current.getOwner()).getName();
                String name = current.getName();
                String display = name != null ? "§b" + name + " §8(§7" + ownerName + "§8)" : "§b" + ownerName;
                player.sendMessage(plugin.getMsg().text("claim.info.other_claimed", Map.of("owner", display)));
                player.sendMessage(plugin.getMsg().text("claim.info.other_tier", Map.of(
                        "tier", String.valueOf(current.getTier()),
                        "radius", String.valueOf(current.getRadius()))));
                return;
            }
            player.sendMessage(plugin.getMsg().text("claim.info.wild"));
            return;
        }

        player.sendMessage("§8§m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤");
        String nameDisplay = claim.getName() != null ? "§b" + claim.getName() : "§7Unnamed";
        player.sendMessage("§6§l  Claim Info  §8|  " + nameDisplay);
        player.sendMessage("§8§m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏏⏤");
        player.sendMessage("§8▶ §7Tier: §f" + claim.getTier() + "  §8|  §7Radius: §f" + claim.getRadius()
                + "  §8|  §7Center: §f" + claim.getCenterX() + ", " + claim.getCenterZ());
        player.sendMessage("§8▶ §7World: §f" + claim.getWorld().getName());
        player.sendMessage("§8▶ §7Trusted: §f" + claim.getAllowedPlayers().size()
                + "  §8|  §7Banned: §f" + claim.getBannedPlayers().size());

        if (claim.hasCustomSpawn()) {
            Location spawn = claim.getSpawnLocation();
            player.sendMessage("§8▶ §7Spawn: §f" + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ());
        }

        boolean borderShowing = plugin.getClaimBorder().isShowing(player.getUniqueId());
        player.sendMessage("§8▶ §7Border: " + (borderShowing ? "§a✔" : "§c✘"));

        if (plugin.getClaimManager().canUpgrade(player)) {
            int cost = plugin.getClaimManager().getUpgradeCost(player);
            player.sendMessage("§8▶ §7Upgrade: §e/$claim upgrade §7(§a$" + cost + "§7)");
        } else {
            player.sendMessage("§8▶ §7Upgrade: §6MAX TIER");
        }

        if (plugin.getConfig().getBoolean("tax.enabled", false)) {
            List<Integer> costs = plugin.getConfig().getIntegerList("tax.cost-per-tier");
            int defaultRadius = plugin.getConfig().getInt("default-radius", 5);
            int tier = claim.getTier();
            int baseCost = tier <= costs.size() ? costs.get(tier - 1) : 0;
            if (baseCost > 0) {
                int taxCost = (int) Math.round((double) baseCost * claim.getRadius() / defaultRadius);
                long next = claim.getTaxNextDue();
                String dueStr;
                if (next > 0) {
                    long remaining = next - System.currentTimeMillis();
                    if (remaining <= 0) {
                        dueStr = "§cOverdue";
                    } else {
                        dueStr = formatDuration(remaining);
                    }
                } else {
                    long interval = plugin.getConfig().getInt("tax.interval-hours", 24);
                    dueStr = "§7~" + interval + "h";
                }
                player.sendMessage("§8▶ §7Tax: §f$" + taxCost + "  §8|  §7Next: §f" + dueStr);
                if (claim.getTaxGraceEnd() > 0 && System.currentTimeMillis() > claim.getTaxGraceEnd()) {
                    player.sendMessage("  §c⚠ Claim is delinquent! Will be abandoned soon.");
                }
            }
        }

        player.sendMessage("§8§m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤");
    }

    private String formatDuration(long millis) {
        if (millis <= 0) return "§cNow";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    private void trustPlayer(Player player, String[] args) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.trust.no_claim"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getMsg().text("claim.trust.usage"));
            return;
        }
        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getMsg().text("claim.trust.not_found", Map.of("target", targetName)));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.trust.self"));
            return;
        }
        if (claim.isTrusted(target.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.trust.already_trusted", Map.of("target", targetName)));
            return;
        }

        Set<ClaimPermission> finalPerms;
        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            finalPerms = EnumSet.allOf(ClaimPermission.class);
        } else {
            finalPerms = new HashSet<>();
            for (int i = 2; i < args.length; i++) {
                switch (args[i].toLowerCase()) {
                    case "build" -> finalPerms.add(ClaimPermission.BUILD);
                    case "chest" -> finalPerms.add(ClaimPermission.CHEST);
                    case "interact" -> finalPerms.add(ClaimPermission.INTERACT);
                    default -> player.sendMessage(plugin.getMsg().text("claim.trust.unknown_perm", Map.of("perm", args[i])));
                }
            }
        }
        ClaimTrustEvent trustEvent = new ClaimTrustEvent(player, target.getUniqueId(), claim, finalPerms);
        plugin.getServer().getPluginManager().callEvent(trustEvent);
        if (trustEvent.isCancelled()) {
            player.sendMessage(plugin.getMsg().text("claim.trust.cancelled"));
            return;
        }
        if (finalPerms.isEmpty() || finalPerms.size() == ClaimPermission.values().length) {
            claim.addAllowed(target.getUniqueId());
            player.sendMessage(plugin.getMsg().text("claim.trust.success_full", Map.of("target", targetName)));
        } else {
            claim.addAllowed(target.getUniqueId(), finalPerms);
            player.sendMessage(plugin.getMsg().text("claim.trust.success_partial", Map.of("target", targetName, "perms", finalPerms.toString())));
        }
        claimManager.saveAll();
        claimManager.logClaimEvent(claim.getOwner().toString(), "TRUST", targetName, "Trusted in claim");
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            String ownerName = player.getName();
            onlineTarget.sendMessage(plugin.getMsg().text("notify.trusted", Map.of("owner", ownerName)));
        }
    }

    private void untrustPlayer(Player player, String targetName) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.untrust.no_claim"));
            return;
        }
        if (targetName == null) {
            player.sendMessage(plugin.getMsg().text("claim.untrust.usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) {
            player.sendMessage(plugin.getMsg().text("claim.untrust.not_found", Map.of("target", targetName)));
            return;
        }
        if (!claim.isTrusted(target.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.untrust.not_trusted", Map.of("target", targetName)));
            return;
        }
        ClaimUntrustEvent untrustEvent = new ClaimUntrustEvent(player, target.getUniqueId(), claim);
        plugin.getServer().getPluginManager().callEvent(untrustEvent);
        if (untrustEvent.isCancelled()) return;
        claim.removeAllowed(target.getUniqueId());
        claimManager.saveAll();
        claimManager.logClaimEvent(claim.getOwner().toString(), "UNTRUST", targetName, "Untrusted from claim");
        player.sendMessage(plugin.getMsg().text("claim.untrust.success", Map.of("target", targetName)));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            String ownerName = player.getName();
            onlineTarget.sendMessage(plugin.getMsg().text("notify.untrusted", Map.of("owner", ownerName)));
        }
    }

    private void abandonClaim(Player player, String[] args) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.abandon.no_claim"));
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
            if (!abandonConfirm.remove(player.getUniqueId())) {
                player.sendMessage(plugin.getMsg().text("claim.abandon.no_request"));
                return;
            }
            claimManager.removeClaim(player.getUniqueId());
            player.sendMessage(plugin.getMsg().text("claim.abandon.success"));
        } else {
            abandonConfirm.add(player.getUniqueId());
            player.sendMessage(plugin.getMsg().text("claim.abandon.confirm"));
            player.sendMessage(plugin.getMsg().text("claim.abandon.confirm_tip"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> abandonConfirm.remove(player.getUniqueId()), 600L);
        }
    }

    private void teleportHome(Player player) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.home.no_claim"));
            return;
        }

        UUID pid = player.getUniqueId();
        long now = System.currentTimeMillis();
        int cooldown = plugin.getConfig().getInt("home-cooldown", 5);
        long last = plugin.getHomeCooldowns().getOrDefault(pid, 0L);
        long elapsed = (now - last) / 1000;
        if (elapsed < cooldown) {
            long remaining = cooldown - elapsed;
            player.sendMessage(plugin.getMsg().text("claim.home.cooldown", Map.of("time", String.valueOf(remaining))));
            return;
        }

        Location target;
        if (claim.hasCustomSpawn()) {
            target = claim.getSpawnLocation().clone();
        } else {
            World world = claim.getWorld();
            int x = claim.getCenterX();
            int z = claim.getCenterZ();
            int y = world.getHighestBlockYAt(x, z) + 1;
            target = new Location(world, x + 0.5, y, z + 0.5);
        }
        player.teleport(target);
        plugin.getHomeCooldowns().put(pid, now);
        player.sendMessage(plugin.getMsg().text("claim.home.success"));
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("landclaim.admin")) {
            player.sendMessage(plugin.getMsg().text("claim.admin.no_perm"));
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("bypass")) {
            boolean disabled = plugin.toggleAdminBypass(player.getUniqueId());
            if (disabled) {
                player.sendMessage(plugin.getMsg().text("claim.admin.bypass_disabled"));
            } else {
                player.sendMessage(plugin.getMsg().text("claim.admin.bypass_enabled"));
            }
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("tax")) {
            handleAdminTax(player, args);
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("remove")) {
            handleAdminRemove(player, args);
            return;
        }
        new AdminGUI(plugin, player).open();
    }

    private void handleAdminTax(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §f/claim admin tax <list|forgive> [player]");
            return;
        }
        if (args[2].equalsIgnoreCase("list")) {
            List<Claim> delinquent = claimManager.getAllClaims().stream()
                    .filter(c -> c.getTaxGraceEnd() > 0 && System.currentTimeMillis() > c.getTaxNextDue())
                    .sorted((a, b) -> Long.compare(b.getTaxGraceEnd(), a.getTaxGraceEnd()))
                    .toList();
            if (delinquent.isEmpty()) {
                player.sendMessage("§aNo delinquent claims found.");
                return;
            }
            player.sendMessage("§6§l=== Delinquent Claims ===");
            for (Claim c : delinquent) {
                String name = c.getName();
                if (name == null) name = plugin.getServer().getOfflinePlayer(c.getOwner()).getName();
                long remaining = c.getTaxGraceEnd() - System.currentTimeMillis();
                String timeStr = remaining > 0 ? formatDuration(remaining) : "§cANY MOMENT";
                player.sendMessage("§e" + name + " §8- §7grace: §f" + timeStr);
            }
            return;
        }
        if (args[2].equalsIgnoreCase("forgive") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[3]);
            if (target == null) {
                player.sendMessage("§cPlayer not found.");
                return;
            }
            Claim c = claimManager.getClaim(target.getUniqueId());
            if (c == null) {
                player.sendMessage("§cThat player has no claim.");
                return;
            }
            c.setTaxGraceEnd(0);
            c.setTaxNextDue(0);
            claimManager.saveAll();
            claimManager.logClaimEvent(target.getUniqueId().toString(), "TAX_FORGIVE", player.getName(),
                    "Tax forgiven by admin");
            player.sendMessage("§aTax cleared for §f" + target.getName());
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage("§aYour claim taxes have been forgiven by an admin.");
            }
            return;
        }
        player.sendMessage("§cUsage: §f/claim admin tax <list|forgive> [player]");
    }

    private void handleAdminRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §f/claim admin remove <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }
        Claim c = claimManager.getClaim(target.getUniqueId());
        if (c == null) {
            player.sendMessage("§cThat player has no claim.");
            return;
        }
        claimManager.removeClaim(target.getUniqueId());
        claimManager.logClaimEvent(target.getUniqueId().toString(), "ADMIN_REMOVE", player.getName(),
                "Claim removed by admin");
        player.sendMessage("§cRemoved §f" + target.getName() + "'s §cclaim.");
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage("§cYour claim has been removed by an admin.");
        }
    }

    private void setName(Player player, String[] args) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.name.no_claim"));
            return;
        }
        if (args.length < 2) {
            String current = claim.getName();
            if (current != null) {
                player.sendMessage(plugin.getMsg().text("claim.name.current", Map.of("name", current)));
            } else {
                player.sendMessage(plugin.getMsg().text("claim.name.usage"));
            }
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (name.length() > 100) {
            player.sendMessage(plugin.getMsg().text("claim.name.too_long"));
            return;
        }
        String oldName = claim.getName();
        claim.setName(name);
        claimManager.saveAll();
        claimManager.logClaimEvent(claim.getOwner().toString(), "RENAME", player.getName(),
                "Renamed from '" + (oldName != null ? oldName : "") + "' to '" + name + "'");
        player.sendMessage(plugin.getMsg().text("claim.name.success", Map.of("name", name)));
    }

    private void transferClaim(Player player, String[] args) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.transfer.no_claim"));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            UUID targetUuid = transferConfirmTargets.remove(player.getUniqueId());
            if (targetUuid == null) {
                player.sendMessage(plugin.getMsg().text("claim.transfer.no_request"));
                return;
            }
            claimManager.transferClaim(player.getUniqueId(), targetUuid);
            player.sendMessage(plugin.getMsg().text("claim.transfer.success", Map.of("target", Bukkit.getOfflinePlayer(targetUuid).getName())));
            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            if (onlineTarget != null) {
                onlineTarget.sendMessage(plugin.getMsg().text("notify.transferred"));
            }
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getMsg().text("claim.transfer.usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getMsg().text("claim.transfer.not_found", Map.of("target", args[1])));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.transfer.self"));
            return;
        }
        if (claimManager.getClaim(target.getUniqueId()) != null) {
            player.sendMessage(plugin.getMsg().text("claim.transfer.target_has_claim"));
            return;
        }
        transferConfirmTargets.put(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(plugin.getMsg().text("claim.transfer.confirm", Map.of("target", target.getName())));
        player.sendMessage(plugin.getMsg().text("claim.transfer.confirm_tip"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> transferConfirmTargets.remove(player.getUniqueId()), 600L);
    }

    private void banPlayer(Player player, String[] args) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.ban.no_claim"));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            UUID targetUuid = banConfirmTargets.remove(player.getUniqueId());
            if (targetUuid == null) {
                player.sendMessage(plugin.getMsg().text("claim.ban.no_request"));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            ClaimBanEvent banEvent = new ClaimBanEvent(player, targetUuid, claim);
            plugin.getServer().getPluginManager().callEvent(banEvent);
            if (banEvent.isCancelled()) return;
            claim.ban(targetUuid);
            claimManager.saveAll();
            claimManager.logClaimEvent(claim.getOwner().toString(), "BAN", target.getName(), "Banned from claim");
            player.sendMessage(plugin.getMsg().text("claim.ban.success", Map.of("target", target.getName())));
            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            if (onlineTarget != null) {
                onlineTarget.sendMessage(plugin.getMsg().text("notify.banned", Map.of("owner", player.getName())));
            }
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getMsg().text("claim.ban.usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getMsg().text("claim.ban.not_found", Map.of("target", args[1])));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.ban.self"));
            return;
        }
        if (claim.isBanned(target.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.ban.already_banned", Map.of("target", target.getName())));
            return;
        }
        banConfirmTargets.put(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(plugin.getMsg().text("claim.ban.confirm", Map.of("target", target.getName())));
        player.sendMessage(plugin.getMsg().text("claim.ban.confirm_tip"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> banConfirmTargets.remove(player.getUniqueId()), 600L);
    }

    private void unbanPlayer(Player player, String[] args) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.unban.no_claim"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getMsg().text("claim.unban.usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getMsg().text("claim.unban.not_found", Map.of("target", args[1])));
            return;
        }
        if (!claim.isBanned(target.getUniqueId())) {
            player.sendMessage(plugin.getMsg().text("claim.unban.not_banned", Map.of("target", target.getName())));
            return;
        }
        ClaimUnbanEvent unbanEvent = new ClaimUnbanEvent(player, target.getUniqueId(), claim);
        plugin.getServer().getPluginManager().callEvent(unbanEvent);
        if (unbanEvent.isCancelled()) return;
        claim.unban(target.getUniqueId());
        claimManager.saveAll();
        claimManager.logClaimEvent(claim.getOwner().toString(), "UNBAN", target.getName(), "Unbanned from claim");
        player.sendMessage(plugin.getMsg().text("claim.unban.success", Map.of("target", target.getName())));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            String ownerName = player.getName();
            onlineTarget.sendMessage(plugin.getMsg().text("notify.unbanned", Map.of("owner", ownerName)));
        }
    }

    private void setSpawn(Player player) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.setspawn.no_claim"));
            return;
        }
        Location loc = player.getLocation();
        if (!claim.contains(loc)) {
            player.sendMessage(plugin.getMsg().text("claim.setspawn.outside"));
            return;
        }
        claim.setSpawnLocation(loc);
        claimManager.saveAll();
        player.sendMessage(plugin.getMsg().text("claim.setspawn.success"));
    }

    private void showTop(Player player) {
        List<Claim> sorted = claimManager.getAllClaims().stream()
                .sorted((a, b) -> {
                    int radiusCmp = Integer.compare(b.getRadius(), a.getRadius());
                    if (radiusCmp != 0) return radiusCmp;
                    return Integer.compare(b.getTier(), a.getTier());
                })
                .limit(10)
                .toList();

        player.sendMessage(plugin.getMsg().text("claim.top.header"));
        if (sorted.isEmpty()) {
            player.sendMessage(plugin.getMsg().text("claim.top.empty"));
            return;
        }
        int i = 1;
        for (Claim c : sorted) {
            String name = c.getName();
            if (name == null) name = plugin.getServer().getOfflinePlayer(c.getOwner()).getName();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("rank", String.valueOf(i++));
            placeholders.put("name", name);
            placeholders.put("tier", String.valueOf(c.getTier()));
            placeholders.put("radius", String.valueOf(c.getRadius()));
            placeholders.put("world", c.getWorld().getName());
            player.sendMessage(plugin.getMsg().text("claim.top.entry", placeholders));
        }
    }

    private void toggleNotify(Player player) {
        boolean enabled = claimManager.toggleNotify(player.getUniqueId());
        player.sendMessage(plugin.getMsg().text(enabled ? "gui.notify.enabled" : "gui.notify.disabled"));
    }

    private void showLogs(Player player) {
        Claim claim = claimManager.getClaim(player.getUniqueId());
        if (claim == null) {
            player.sendMessage(plugin.getMsg().text("claim.logs.no_claim"));
            return;
        }
        List<Map<String, Object>> logs = claimManager.getClaimLogs(claim.getOwner().toString(), 20);
        if (logs.isEmpty()) {
            player.sendMessage(plugin.getMsg().text("claim.logs.empty"));
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        player.sendMessage(plugin.getMsg().text("claim.logs.header"));
        for (Map<String, Object> entry : logs) {
            String time = sdf.format(new Date((long) entry.get("timestamp")));
            player.sendMessage(plugin.getMsg().text("claim.logs.entry", Map.of(
                    "time", time,
                    "action", String.valueOf(entry.get("action")),
                    "player", String.valueOf(entry.get("player")),
                    "details", String.valueOf(entry.get("details")))));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("gui", "upgrade", "info", "border", "trust",
                    "untrust", "abandon", "home", "admin", "name", "transfer", "ban", "unban",
                    "setspawn", "top", "logs", "notify"));
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("transfer") || args[0].equalsIgnoreCase("ban")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("untrust") && sender instanceof Player player) {
                Claim claim = claimManager.getClaim(player.getUniqueId());
                if (claim != null) {
                    return claim.getAllowedPlayers().keySet().stream()
                            .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                            .filter(Objects::nonNull)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
            if (args[0].equalsIgnoreCase("unban") && sender instanceof Player player) {
                Claim claim = claimManager.getClaim(player.getUniqueId());
                if (claim != null) {
                    return claim.getBannedPlayers().stream()
                            .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                            .filter(Objects::nonNull)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
            if (args[0].equalsIgnoreCase("abandon")) {
                return List.of("confirm");
            }
            if (args[0].equalsIgnoreCase("admin")) {
                return List.of("bypass", "tax", "remove").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("name") && sender instanceof Player player) {
                Claim claim = claimManager.getClaim(player.getUniqueId());
                if (claim != null && claim.getName() != null) {
                    return List.of(claim.getName()).stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
            if (args[0].equalsIgnoreCase("transfer") && sender instanceof Player player) {
                if (transferConfirmTargets.containsKey(player.getUniqueId())) {
                    return List.of("confirm");
                }
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("ban") && sender instanceof Player player) {
                if (banConfirmTargets.containsKey(player.getUniqueId())) {
                    return List.of("confirm");
                }
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("notify") || args[0].equalsIgnoreCase("border")) {
                return List.of("on", "off").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trust")) {
            return List.of("build", "chest", "interact", "all");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("tax")) {
                return List.of("list", "forgive").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .toList();
            }
            if (args[1].equalsIgnoreCase("remove")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("tax") && args[2].equalsIgnoreCase("forgive")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
