package com.landclaim.gui.admin;

import com.landclaim.LandClaimPlugin;
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

public class AdminGUI implements Listener {

    private static final int ROWS = 3;

    private final LandClaimPlugin plugin;
    private final Player player;
    private Inventory inventory;
    private boolean closed;

    public AdminGUI(LandClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lAdmin Panel"));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        int total = plugin.getClaimManager().getAllClaims().size();
        inventory.setItem(11, createItem(Material.BOOK, "§e§l📋 Claim List",
                "§7» Total claims: §f" + total,
                "",
                "§eClick to browse all claims"));

        List<String> worlds = plugin.getClaimManager().getBlacklistWorlds();
        List<Map<?, ?>> areas = plugin.getClaimManager().getBlacklistAreas();
        inventory.setItem(12, createItem(Material.BARRIER, "§c§l🚫 Blacklist Manager",
                "§7» Worlds: §f" + worlds.size(),
                "§7» Areas: §f" + areas.size(),
                "",
                "§eClick to manage"));

        inventory.setItem(13, createItem(Material.COMMAND_BLOCK, "§6§l⚙ Config",
                "§7Modify plugin settings",
                "§7via an in-game GUI",
                "",
                "§eClick to manage"));

        int globalCount = plugin.getClaimManager().getGlobalTrustedPlayers().size();
        inventory.setItem(14, createItem(Material.PLAYER_HEAD, "§d§l🌐 Global Trust",
                "§7» Trusted: §f" + globalCount,
                "",
                "§eClick to manage"));

        inventory.setItem(15, createItem(Material.REDSTONE, "§6§l🔄 Reload",
                "§7Save all data and reload config",
                "",
                "§eClick to reload"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        if (slot == 11) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new ClaimListGUI(plugin, player, 0).open(), 2L);
        } else if (slot == 12) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new BlacklistGUI(plugin, player).open(), 2L);
        } else if (slot == 13) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new ConfigGUI(plugin, player, 0).open(), 2L);
        } else if (slot == 14) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new GlobalTrustGUI(plugin, player).open(), 2L);
        } else if (slot == 15) {
            plugin.getClaimManager().saveAll();
            plugin.reload();
            player.sendMessage(plugin.getMsg().text("gui.admin.reloaded"));
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && !closed) {
            closed = true;
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
