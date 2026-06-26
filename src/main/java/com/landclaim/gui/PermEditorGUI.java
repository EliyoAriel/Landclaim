package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class PermEditorGUI implements Listener {

    private static final int ROWS = 3;

    private final LandClaimPlugin plugin;
    private final Player player;
    private final Claim claim;
    private final UUID targetUuid;
    private Inventory inventory;

    public PermEditorGUI(LandClaimPlugin plugin, Player player, UUID targetUuid) {
        this.plugin = plugin;
        this.player = player;
        this.claim = plugin.getClaimManager().getClaim(player.getUniqueId());
        this.targetUuid = targetUuid;
    }

    public void open() {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String name = target.getName() != null ? target.getName() : targetUuid.toString().substring(0, 8);
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lPermissions: " + name));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String name = target.getName() != null ? target.getName() : "Unknown";
        ItemStack head = getHead(target, "§e" + name);
        inventory.setItem(4, head);

        EnumSet<ClaimPermission> perms = claim.getPermissions(targetUuid);
        if (perms == null) perms = EnumSet.noneOf(ClaimPermission.class);

        inventory.setItem(11, makeToggle(Material.DIAMOND_PICKAXE, "Build", "Break & place blocks", perms.contains(ClaimPermission.BUILD)));
        inventory.setItem(13, makeToggle(Material.CHEST, "Chest", "Open containers & crafting", perms.contains(ClaimPermission.CHEST)));
        inventory.setItem(15, makeToggle(Material.LEVER, "Interact", "Use doors, levers, buttons", perms.contains(ClaimPermission.INTERACT)));

        boolean all = perms.size() == ClaimPermission.values().length;
        inventory.setItem(20, createItem(all ? Material.GREEN_DYE : Material.GRAY_DYE,
                (all ? "§a§l" : "§7§l") + "Full Access",
                "§7Toggle all permissions on/off"));

        inventory.setItem(22, createItem(Material.ARROW, "§c§lBack", "§7Return to trusted players"));

        inventory.setItem(24, createItem(Material.BARRIER, "§c§lUntrust Player",
                "§7Remove " + name + " from your claim"));
    }

    private ItemStack makeToggle(Material mat, String name, String desc, boolean enabled) {
        Material display = enabled ? mat : Material.GRAY_DYE;
        String status = enabled ? "§a✔ Enabled" : "§c✘ Disabled";
        ItemStack item = createItem(display, (enabled ? "§a§l" : "§7§l") + name, desc, "", status, "§7Click to toggle");
        return item;
    }

    private ItemStack getHead(OfflinePlayer offline, String name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.setOwningPlayer(offline);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name.replaceAll("&", "§")));
        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        EnumSet<ClaimPermission> perms = claim.getPermissions(targetUuid);
        if (perms == null) perms = EnumSet.noneOf(ClaimPermission.class);

        if (slot == 11) {
            toggle(perms, ClaimPermission.BUILD);
        } else if (slot == 13) {
            toggle(perms, ClaimPermission.CHEST);
        } else if (slot == 15) {
            toggle(perms, ClaimPermission.INTERACT);
        } else if (slot == 20) {
            boolean all = perms.size() == ClaimPermission.values().length;
            if (all) {
                perms.clear();
            } else {
                perms.addAll(EnumSet.allOf(ClaimPermission.class));
            }
        } else if (slot == 22) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new TrustGUI(plugin, player).open();
            }, 2L);
            return;
        } else if (slot == 24) {
            player.closeInventory();
            String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new ConfirmGUI(plugin, player, "Untrust " + targetName + "?",
                        () -> {
                            claim.removeAllowed(targetUuid);
                            plugin.getClaimManager().saveAll();
                            player.sendMessage(plugin.getMsg().text("claim.perm_editor.untrusted", Map.of("target", targetName != null ? targetName : targetUuid.toString().substring(0, 8))));
                            new TrustGUI(plugin, player).open();
                        },
                        () -> new PermEditorGUI(plugin, player, targetUuid).open()
                ).open();
            }, 2L);
            return;
        } else {
            return;
        }

        claim.setPermissions(targetUuid, perms);
        plugin.getClaimManager().saveAll();
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new PermEditorGUI(plugin, player, targetUuid).open();
        }, 2L);
    }

    private void toggle(EnumSet<ClaimPermission> perms, ClaimPermission perm) {
        if (perms.contains(perm)) perms.remove(perm);
        else perms.add(perm);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
