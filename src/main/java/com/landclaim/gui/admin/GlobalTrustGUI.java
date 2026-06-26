package com.landclaim.gui.admin;

import com.landclaim.LandClaimPlugin;
import com.landclaim.gui.ConfirmGUI;
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

public class GlobalTrustGUI implements Listener {

    private static final int ROWS = 6;

    private final LandClaimPlugin plugin;
    private final Player player;
    private Inventory inventory;
    private boolean closed;
    private List<UUID> trustedList;

    public GlobalTrustGUI(LandClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lGlobal Trusted Players"));
        this.trustedList = new ArrayList<>(plugin.getClaimManager().getGlobalTrustedPlayers());
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        trustedList = new ArrayList<>(plugin.getClaimManager().getGlobalTrustedPlayers());
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        inventory.setItem(4, createItem(Material.PLAYER_HEAD, "§a§lAdd Player",
                "§7Click to add a globally trusted player",
                "§7Type the player name in chat"));

        inventory.setItem(49, createItem(Material.ARROW, "§c§lBack",
                "§7Return to admin panel"));

        for (int i = 0; i < trustedList.size() && i < 36; i++) {
            UUID uuid = trustedList.get(i);
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            String name = off.getName() != null ? off.getName() : uuid.toString().substring(0, 8);
            ItemStack head = createItem(Material.PLAYER_HEAD, "§e" + name,
                    "§7UUID: §f" + uuid,
                    "",
                    "§cClick to remove global trust");
            try {
                SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                skullMeta.setPlayerProfile(off.getPlayerProfile());
                head.setItemMeta(skullMeta);
            } catch (Exception ignored) {}
            inventory.setItem(9 + i, head);
        }
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name.replaceAll("&", "§")));
        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line.replaceAll("&", "§")));
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
        if (slot == 4) {
            player.closeInventory();
            plugin.setPendingInput(player.getUniqueId(), "globaltrust_add");
            player.sendMessage(plugin.getMsg().text("gui.global_trust.add_prompt"));
            player.sendMessage(plugin.getMsg().text("gui.global_trust.cancel_tip"));
        } else if (slot == 49) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new AdminGUI(plugin, player).open(), 2L);
        } else if (slot >= 9 && slot < 9 + trustedList.size()) {
            int index = slot - 9;
            if (index < trustedList.size()) {
                UUID target = trustedList.get(index);
                String targetName = Bukkit.getOfflinePlayer(target).getName();
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    new ConfirmGUI(plugin, player, "Remove " + targetName + "?",
                            () -> {
                                plugin.getClaimManager().removeGlobalTrust(target);
                                player.sendMessage(plugin.getMsg().text("gui.global_trust.removed", Map.of("target", targetName != null ? targetName : target.toString().substring(0, 8))));
                                new GlobalTrustGUI(plugin, player).open();
                            },
                            () -> new GlobalTrustGUI(plugin, player).open()
                    ).open();
                }, 2L);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && !closed) {
            closed = true;
            HandlerList.unregisterAll(this);
        }
    }
}
