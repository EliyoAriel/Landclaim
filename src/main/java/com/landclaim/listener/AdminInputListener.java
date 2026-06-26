package com.landclaim.listener;

import com.landclaim.LandClaimPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;

public class AdminInputListener implements Listener {

    private final LandClaimPlugin plugin;

    public AdminInputListener(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String pending = plugin.getPendingInput(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        plugin.setPendingInput(player.getUniqueId(), null);

        String msg = event.getMessage().trim();

        Player finalPlayer = player;
        String finalMsg = msg;
        String finalPending = pending;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (finalMsg.equalsIgnoreCase("cancel")) {
                finalPlayer.sendMessage(plugin.getMsg().text("gui.general.cancelled"));
                return;
            }
            if (finalPending.equals("blacklist_world")) {
                plugin.getClaimManager().addBlacklistWorld(finalMsg);
                finalPlayer.sendMessage(plugin.getMsg().text("gui.blacklist.add_world_success", Map.of("world", finalMsg)));
                new com.landclaim.gui.admin.BlacklistGUI(plugin, finalPlayer).open();

            } else if (finalPending.equals("globaltrust_add")) {
                org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayerIfCached(finalMsg);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    finalPlayer.sendMessage(plugin.getMsg().text("gui.global_trust.not_found"));
                } else {
                    plugin.getClaimManager().addGlobalTrust(target.getUniqueId());
                    finalPlayer.sendMessage(plugin.getMsg().text("gui.global_trust.added", Map.of("target", target.getName())));
                }
                new com.landclaim.gui.admin.GlobalTrustGUI(plugin, finalPlayer).open();

            } else if (finalPending.equals("trust_add")) {
                org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayerIfCached(finalMsg);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    finalPlayer.sendMessage(plugin.getMsg().text("claim.trust.not_found", Map.of("target", finalMsg)));
                    return;
                }
                org.bukkit.OfflinePlayer finalTarget = target;
                com.landclaim.claim.Claim claim = plugin.getClaimManager().getClaim(finalPlayer.getUniqueId());
                if (claim == null) {
                    finalPlayer.sendMessage(plugin.getMsg().text("claim.trust.no_claim"));
                    return;
                }
                if (finalTarget.getUniqueId().equals(finalPlayer.getUniqueId())) {
                    finalPlayer.sendMessage(plugin.getMsg().text("claim.trust.self"));
                    return;
                }
                if (claim.isTrusted(finalTarget.getUniqueId())) {
                    finalPlayer.sendMessage(plugin.getMsg().text("claim.trust.already_trusted", Map.of("target", finalTarget.getName())));
                    return;
                }
                java.util.Set<com.landclaim.claim.ClaimPermission> finalPerms = java.util.EnumSet.allOf(com.landclaim.claim.ClaimPermission.class);
                com.landclaim.api.ClaimTrustEvent trustEvent = new com.landclaim.api.ClaimTrustEvent(finalPlayer, finalTarget.getUniqueId(), claim, finalPerms);
                plugin.getServer().getPluginManager().callEvent(trustEvent);
                if (trustEvent.isCancelled()) {
                    finalPlayer.sendMessage(plugin.getMsg().text("claim.trust.cancelled"));
                    return;
                }
                claim.addAllowed(finalTarget.getUniqueId());
                plugin.getClaimManager().saveAll();
                plugin.getClaimManager().logClaimEvent(claim.getOwner().toString(), "TRUST", finalPlayer.getName(), finalTarget.getName());
                finalPlayer.sendMessage(plugin.getMsg().text("claim.trust.success_full", Map.of("target", finalTarget.getName())));
                org.bukkit.entity.Player onlineTarget = plugin.getServer().getPlayer(finalTarget.getUniqueId());
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(plugin.getMsg().text("notify.trusted", Map.of("owner", finalPlayer.getName())));
                }
                new com.landclaim.gui.TrustGUI(plugin, finalPlayer).open();

            } else if (finalPending.equals("blacklist_area")) {
                String[] parts = finalMsg.split("\\s+");
                if (parts.length < 4) {
                    finalPlayer.sendMessage(plugin.getMsg().text("gui.blacklist.invalid_format"));
                    new com.landclaim.gui.admin.BlacklistGUI(plugin, finalPlayer).open();
                    return;
                }
                try {
                    String world = parts[0];
                    int x1 = Integer.parseInt(parts[1]);
                    int z1 = Integer.parseInt(parts[2]);
                    int x2 = Integer.parseInt(parts[3]);
                    int z2 = parts.length > 4 ? Integer.parseInt(parts[4]) : x2;
                    plugin.getClaimManager().addBlacklistArea(world, x1, z1, x2, z2);
                    finalPlayer.sendMessage(plugin.getMsg().text("gui.blacklist.add_area_success"));
                } catch (NumberFormatException e) {
                    finalPlayer.sendMessage(plugin.getMsg().text("gui.blacklist.invalid_coords"));
                }
                new com.landclaim.gui.admin.BlacklistGUI(plugin, finalPlayer).open();

            } else if (finalPending.startsWith("config:")) {
                String key = finalPending.substring("config:".length());
                try {
                    Object current = plugin.getConfig().get(key);
                    if (current instanceof Integer) {
                        plugin.getConfig().set(key, Integer.parseInt(finalMsg));
                    } else if (current instanceof Double) {
                        plugin.getConfig().set(key, Double.parseDouble(finalMsg));
                    } else if (current instanceof Long) {
                        plugin.getConfig().set(key, Long.parseLong(finalMsg));
                    } else if (current instanceof Boolean) {
                        plugin.getConfig().set(key, Boolean.parseBoolean(finalMsg));
                    } else {
                        plugin.getConfig().set(key, finalMsg);
                    }
                    plugin.saveConfig();
                    plugin.reload();
                    finalPlayer.sendMessage("§aSet §f" + key + " §ato §f" + plugin.getConfig().get(key));
                } catch (NumberFormatException e) {
                    finalPlayer.sendMessage("§cInvalid number format for that config key.");
                }
                new com.landclaim.gui.admin.ConfigGUI(plugin, finalPlayer, 0).open();
            }
        });
    }
}
