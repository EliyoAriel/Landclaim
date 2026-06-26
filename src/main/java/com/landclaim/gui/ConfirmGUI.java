package com.landclaim.gui;

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

public class ConfirmGUI implements Listener {

    private final LandClaimPlugin plugin;
    private final Player player;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private Inventory inventory;
    private boolean closed;

    public ConfirmGUI(LandClaimPlugin plugin, Player player, String title, Runnable onConfirm, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.inventory = Bukkit.createInventory(null, 9, Component.text("§8§l" + title));
    }

    public void open() {
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(3, createItem(Material.GREEN_DYE, "§a§lConfirm", "§7Click to confirm"));
        inventory.setItem(5, createItem(Material.RED_DYE, "§c§lCancel", "§7Click to cancel"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        int slot = event.getRawSlot();
        if (slot == 3) {
            closed = true;
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, onConfirm);
        } else if (slot == 5) {
            closed = true;
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, onCancel);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && !closed) {
            closed = true;
            HandlerList.unregisterAll(this);
            Bukkit.getScheduler().runTask(plugin, onCancel);
        }
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
}
