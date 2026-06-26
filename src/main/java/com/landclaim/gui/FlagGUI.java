package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimFlag;
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

public class FlagGUI implements Listener {

    private static final int ROWS = 4;

    private final LandClaimPlugin plugin;
    private final Player player;
    private final Claim claim;
    private Inventory inventory;

    public FlagGUI(LandClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.claim = plugin.getClaimManager().getClaim(player.getUniqueId());
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lClaim Flags"));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        inventory.setItem(10, makeToggle(Material.DIAMOND_SWORD, "PVP",
                "Allow players to damage each other", claim.getFlag(ClaimFlag.PVP)));
        inventory.setItem(11, makeToggle(Material.SPAWNER, "Mob Spawning",
                "Allow mobs to spawn naturally", claim.getFlag(ClaimFlag.MOB_SPAWNING)));
        inventory.setItem(12, makeToggle(Material.FLINT_AND_STEEL, "Fire Spread",
                "Allow fire to spread", claim.getFlag(ClaimFlag.FIRE_SPREAD)));
        inventory.setItem(13, makeToggle(Material.TNT, "Explosions",
                "Allow TNT and creepers to damage", claim.getFlag(ClaimFlag.EXPLOSIONS)));
        inventory.setItem(14, makeToggle(Material.ENDER_PEARL, "Mob Griefing",
                "Allow endermen to steal blocks", claim.getFlag(ClaimFlag.MOB_GRIEFING)));
        inventory.setItem(15, makeToggle(Material.WHEAT, "Crop Trample",
                "Prevent farmland trampling", claim.getFlag(ClaimFlag.CROP_TRAMPLE)));
        inventory.setItem(16, makeToggle(Material.PISTON, "Piston Protection",
                "Prevent pistons from moving blocks", claim.getFlag(ClaimFlag.PISTON_PROTECTION)));

        inventory.setItem(20, makeToggle(Material.ZOMBIE_HEAD, "Mob Damage",
                "Prevent players damaging monsters", claim.getFlag(ClaimFlag.MOB_DAMAGE)));
        inventory.setItem(21, makeToggle(Material.COW_SPAWN_EGG, "Animal Damage",
                "Prevent players harming animals", claim.getFlag(ClaimFlag.ANIMAL_DAMAGE)));
        inventory.setItem(22, makeToggle(Material.MINECART, "Vehicle Damage",
                "Prevent vehicle destruction", claim.getFlag(ClaimFlag.VEHICLE_DAMAGE)));

        inventory.setItem(31, createItem(Material.ARROW, "§c§lBack",
                "§7Return to claim management"));
    }

    private ItemStack makeToggle(Material mat, String name, String desc, boolean enabled) {
        Material display = enabled ? mat : Material.GRAY_DYE;
        ItemStack item = createItem(display, (enabled ? "§a§l" : "§7§l") + name,
                desc, "",
                (enabled ? "§a✔ Enabled" : "§c✘ Disabled"),
                "§7Click to toggle");
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();

        if (slot == 31) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new ClaimGUI(plugin, player).open();
            }, 2L);
            return;
        }

        ClaimFlag target = switch (slot) {
            case 10 -> ClaimFlag.PVP;
            case 11 -> ClaimFlag.MOB_SPAWNING;
            case 12 -> ClaimFlag.FIRE_SPREAD;
            case 13 -> ClaimFlag.EXPLOSIONS;
            case 14 -> ClaimFlag.MOB_GRIEFING;
            case 15 -> ClaimFlag.CROP_TRAMPLE;
            case 16 -> ClaimFlag.PISTON_PROTECTION;
            case 20 -> ClaimFlag.MOB_DAMAGE;
            case 21 -> ClaimFlag.ANIMAL_DAMAGE;
            case 22 -> ClaimFlag.VEHICLE_DAMAGE;
            default -> null;
        };

        if (target == null) return;

        boolean newValue = !claim.getFlag(target);
        claim.setFlag(target, newValue);
        plugin.getClaimManager().saveAll();
        String flagName = target.name().replace("_", " ").toLowerCase();
        flagName = Character.toUpperCase(flagName.charAt(0)) + flagName.substring(1);
        player.sendMessage(plugin.getMsg().text(newValue ? "claim.flag.toggle" : "claim.flag.untoggle", Map.of("flag", flagName)));
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new FlagGUI(plugin, player).open();
        }, 2L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
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
