package com.landclaim.gui.admin;

import com.landclaim.LandClaimPlugin;
import com.landclaim.gui.ConfirmGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlacklistGUI implements Listener {

    private static final int ROWS = 4;

    private final LandClaimPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public BlacklistGUI(LandClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lBlacklist Manager"));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        List<String> worlds = plugin.getClaimManager().getBlacklistWorlds();
        int ws = 9;
        if (worlds.isEmpty()) {
            inventory.setItem(13, createItem(Material.BARRIER, "§7No blacklisted worlds",
                    "§7Click 'Add World' below to add one"));
        } else {
            for (String world : worlds) {
                if (ws > 17) break;
                inventory.setItem(ws++, createItem(Material.GRASS_BLOCK, "§cWorld: §f" + world,
                        "§7Click to remove from blacklist"));
            }
        }

        List<Map<?, ?>> areas = plugin.getClaimManager().getBlacklistAreas();
        int as = 18;
        if (areas.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, "§7No blacklisted areas",
                    "§7Click 'Add Area' below to add one"));
        } else {
            for (Map<?, ?> area : areas) {
                if (as > 26) break;
                String w = (String) area.get("world");
                int x1 = Math.min((int) area.get("x1"), (int) area.get("x2"));
                int z1 = Math.min((int) area.get("z1"), (int) area.get("z2"));
                int x2 = Math.max((int) area.get("x1"), (int) area.get("x2"));
                int z2 = Math.max((int) area.get("z1"), (int) area.get("z2"));
                inventory.setItem(as++, createItem(Material.FILLED_MAP, "§cArea: §f" + w,
                        "§7From: §f" + x1 + ", " + z1,
                        "§7To:   §f" + x2 + ", " + z2,
                        "",
                        "§7Click to remove from blacklist"));
            }
        }

        inventory.setItem(29, createItem(Material.GRASS_BLOCK, "§a§lAdd World",
                "§7Click, then type the world name in chat",
                "§7Type 'cancel' to abort"));
        inventory.setItem(31, createItem(Material.FILLED_MAP, "§a§lAdd Area",
                "§7Click, then type: §fworld x1 z1 x2 z2",
                "§7Type 'cancel' to abort"));
        inventory.setItem(33, createItem(Material.ARROW, "§c§lBack",
                "§7Return to admin panel"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();

        if (slot == 33) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new AdminGUI(plugin, player).open();
            }, 2L);
            return;
        }

        if (slot == 29) {
            player.closeInventory();
            plugin.setPendingInput(player.getUniqueId(), "blacklist_world");
            player.sendMessage(plugin.getMsg().text("gui.blacklist.add_world_prompt"));
            return;
        }

        if (slot == 31) {
            player.closeInventory();
            plugin.setPendingInput(player.getUniqueId(), "blacklist_area");
            player.sendMessage(plugin.getMsg().text("gui.blacklist.add_area_prompt"));
            return;
        }

        if (slot >= 9 && slot <= 17) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.BARRIER || item.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            List<String> worlds = plugin.getClaimManager().getBlacklistWorlds();
            int idx = slot - 9;
            if (idx < worlds.size()) {
                String removed = worlds.get(idx);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    new ConfirmGUI(plugin, player, "Remove World?",
                            () -> {
                                plugin.getClaimManager().removeBlacklistWorld(removed);
                                player.sendMessage(plugin.getMsg().text("gui.blacklist.remove_world_success", Map.of("world", removed)));
                                new BlacklistGUI(plugin, player).open();
                            },
                            () -> new BlacklistGUI(plugin, player).open()
                    ).open();
                }, 2L);
            }
            return;
        }

        if (slot >= 18 && slot <= 26) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.BARRIER || item.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            List<Map<?, ?>> areas = plugin.getClaimManager().getBlacklistAreas();
            int idx = slot - 18;
            if (idx < areas.size()) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    new ConfirmGUI(plugin, player, "Remove Area?",
                            () -> {
                                plugin.getClaimManager().removeBlacklistArea(idx);
                                player.sendMessage(plugin.getMsg().text("gui.blacklist.remove_area_success"));
                                new BlacklistGUI(plugin, player).open();
                            },
                            () -> new BlacklistGUI(plugin, player).open()
                    ).open();
                }, 2L);
            }
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    static ItemStack createItem(Material material, String name, String... lore) {
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
}
