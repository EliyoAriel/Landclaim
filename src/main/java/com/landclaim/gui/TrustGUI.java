package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimPermission;
import net.kyori.adventure.text.Component;
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

public class TrustGUI implements Listener {

    private static final int ROWS = 3;

    private final LandClaimPlugin plugin;
    private final Player player;
    private final Claim claim;
    private Inventory inventory;

    public TrustGUI(LandClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.claim = plugin.getClaimManager().getClaim(player.getUniqueId());
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lTrusted Players"));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        Map<UUID, EnumSet<ClaimPermission>> trusted = claim.getAllowedPlayers();
        int slot = 10;

        for (Map.Entry<UUID, EnumSet<ClaimPermission>> entry : trusted.entrySet()) {
            if (slot > 15) break;
            OfflinePlayer off = Bukkit.getOfflinePlayer(entry.getKey());
            String name = off.getName() != null ? off.getName() : entry.getKey().toString().substring(0, 8);
            EnumSet<ClaimPermission> perms = entry.getValue();

            List<String> lore = new ArrayList<>();
            lore.add("§7Build: " + (perms.contains(ClaimPermission.BUILD) ? "§a✔" : "§c✘"));
            lore.add("§7Chest: " + (perms.contains(ClaimPermission.CHEST) ? "§a✔" : "§c✘"));
            lore.add("§7Interact: " + (perms.contains(ClaimPermission.INTERACT) ? "§a✔" : "§c✘"));
            lore.add("");
            lore.add("§eLeft-click to edit permissions");
            lore.add("§cRight-click to untrust");

            ItemStack head = getPlayerHead(off, "§e" + name, lore.toArray(new String[0]));
            inventory.setItem(slot++, head);
        }

        ItemStack info = createItem(Material.PLAYER_HEAD, "§a§lHow to Trust",
                "§7Use command: §f/claim trust <player>",
                "§7Example: §f/claim trust Steve");
        inventory.setItem(21, info);

        ItemStack back = createItem(Material.ARROW, "§c§lBack",
                "§7Return to claim management");
        inventory.setItem(22, back);
    }

    private ItemStack getPlayerHead(OfflinePlayer offline, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.setOwningPlayer(offline);
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

        if (slot == 22) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new ClaimGUI(plugin, player).open();
            }, 2L);
            return;
        }

        if (slot >= 10 && slot <= 15) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != Material.PLAYER_HEAD) return;
            if (!(item.getItemMeta() instanceof SkullMeta meta)) return;
            OfflinePlayer target = meta.getOwningPlayer();
            if (target == null || target.getUniqueId() == null) return;

            if (event.isRightClick()) {
                UUID tid = target.getUniqueId();
                String targetName = target.getName() != null ? target.getName() : tid.toString().substring(0, 8);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    new ConfirmGUI(plugin, player, "Untrust " + targetName + "?",
                            () -> {
                                claim.removeAllowed(tid);
                                plugin.getClaimManager().saveAll();
                                player.sendMessage(plugin.getMsg().text("claim.perm_editor.untrusted", Map.of("target", targetName)));
                                new TrustGUI(plugin, player).open();
                            },
                            () -> new TrustGUI(plugin, player).open()
                    ).open();
                }, 2L);
                return;
            }

            player.closeInventory();
            UUID tid = target.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new PermEditorGUI(plugin, player, tid).open();
            }, 2L);
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
