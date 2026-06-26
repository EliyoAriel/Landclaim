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

public class ConfigGUI implements Listener {

    private static final int ROWS = 5;

    private final LandClaimPlugin plugin;
    private final Player player;
    private int page;
    private Inventory inventory;

    private static final List<String[]> PAGES = new ArrayList<>();
    private static final int TIERS_PAGE = 3;

    static {
        PAGES.add(new String[]{
                "default-radius",
                "max-radius",
                "home-cooldown",
                "enter-message"
        });
        PAGES.add(new String[]{
                "protection.block-break",
                "protection.block-place",
                "protection.interact",
                "protection.chest-access",
                "protection.pvp",
                "protection.offline-extra",
                "protection.animal-damage",
                "protection.vehicle-damage"
        });
        PAGES.add(new String[]{
                "tax.enabled",
                "tax.interval-hours",
                "tax.grace-days"
        });
    }

    public ConfigGUI(LandClaimPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lConfig » " + getPageName(page)));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private String getPageName(int p) {
        return switch (p) {
            case 0 -> "§bGeneral";
            case 1 -> "§aProtection";
            case 2 -> "§eTax";
            case 3 -> "§6Tiers";
            default -> "§7Page " + (p + 1);
        };
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        if (page == TIERS_PAGE) {
            populateTiers();
            return;
        }

        String[] keys = page < PAGES.size() ? PAGES.get(page) : new String[0];
        int slot = 10;
        for (String key : keys) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= inventory.getSize() - 9) break;

            Object value = plugin.getConfig().get(key);
            boolean isBool = value instanceof Boolean;
            boolean isNumber = value instanceof Number;
            String displayKey = key.replace("protection.", "").replace("-", " ").replace(".", " > ");
            displayKey = Character.toUpperCase(displayKey.charAt(0)) + displayKey.substring(1);

            String valueStr;
            Material mat;
            if (isBool) {
                boolean val = (Boolean) value;
                mat = val ? Material.LIME_DYE : Material.GRAY_DYE;
                valueStr = val ? "§a✔ Enabled" : "§c✘ Disabled";
            } else {
                mat = Material.PAPER;
                valueStr = "§f" + value;
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7» " + (isBool ? "Value:" : "Value:") + " " + valueStr);
            lore.add("");
            if (isBool) {
                lore.add("§eClick to toggle");
            } else {
                lore.add("§eClick to change");
                lore.add("§7(Type new value in chat)");
            }

            inventory.setItem(slot++, createItem(mat, "§6§l" + displayKey, lore.toArray(new String[0])));
        }

        addNavButtons();
    }

    private void populateTiers() {
        int tierCount = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
        int slot = 10;
        for (int t = 1; t <= tierCount; t++) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= inventory.getSize() - 9) break;

            int radius = plugin.getConfig().getInt("tiers." + t + ".radius");
            int price = plugin.getConfig().getInt("tiers." + t + ".price");

            List<String> lore = new ArrayList<>();
            lore.add("§7» Radius: §f" + radius);
            lore.add("§7» Price: §f$" + price);
            lore.add("");
            lore.add("§eLeft-click: edit radius");
            lore.add("§eRight-click: edit price");
            lore.add("§cShift+right-click: remove tier");

            ItemStack item = createItem(Material.EXPERIENCE_BOTTLE, "§6§lTier " + t,
                    lore.toArray(new String[0]));
            inventory.setItem(slot++, item);
        }

        if (slot < inventory.getSize() - 9) {
            if (slot % 9 == 8) slot += 2;
            ItemStack add = createItem(Material.LIME_DYE, "§a§l+ Add Tier",
                    "§7Click to add a new tier",
                    "§7(you can then edit radius & price)");
            inventory.setItem(slot, add);
        }

        addNavButtons();
    }

    private void addNavButtons() {
        if (page > 0) {
            inventory.setItem(36, createItem(Material.ARROW, "§e§l◀ Previous",
                    "§7Page: " + getPageName(page - 1)));
        }
        if (page < TIERS_PAGE) {
            inventory.setItem(44, createItem(Material.ARROW, "§e§lNext ▶",
                    "§7Page: " + getPageName(page + 1)));
        }

        inventory.setItem(40, createItem(Material.NETHER_STAR, "§a§l✔ Save & Reload",
                "§7Save changes and reload config"));
        inventory.setItem(38, createItem(Material.ARROW, "§c§lBack",
                "§7Return to admin panel"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == 38) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new AdminGUI(plugin, player).open(), 2L);
            return;
        }

        if (slot == 40) {
            plugin.saveConfig();
            plugin.reload();
            player.sendMessage(plugin.getMsg().text("gui.admin.reloaded"));
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new AdminGUI(plugin, player).open(), 2L);
            return;
        }

        if (slot == 36 && page > 0) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new ConfigGUI(plugin, player, page - 1).open(), 2L);
            return;
        }

        if (slot == 44 && page < TIERS_PAGE) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new ConfigGUI(plugin, player, page + 1).open(), 2L);
            return;
        }

        if (page == TIERS_PAGE) {
            handleTierClick(slot, event.getClick());
            return;
        }

        String[] keys = page < PAGES.size() ? PAGES.get(page) : new String[0];
        int idx = slotToIndex(slot);
        if (idx < 0 || idx >= keys.length) return;

        String key = keys[idx];
        Object value = plugin.getConfig().get(key);

        if (value instanceof Boolean) {
            plugin.getConfig().set(key, !((Boolean) value));
            player.sendMessage("§aToggled §f" + key + " §ato §f" + plugin.getConfig().get(key));
            populate();
            player.updateInventory();
        } else {
            player.closeInventory();
            plugin.setPendingInput(player.getUniqueId(), "config:" + key);
            player.sendMessage("§eType the new value for §f" + key + " §ein chat, or type §c'cancel'§e to abort.");
        }
    }

    private void handleTierClick(int slot, org.bukkit.event.inventory.ClickType click) {
        int tierCount = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
        int idx = slotToIndex(slot);
        if (idx < 0) return;

        if (idx == tierCount) {
            int newTier = tierCount + 1;
            plugin.getConfig().set("tiers." + newTier + ".radius", 25);
            plugin.getConfig().set("tiers." + newTier + ".price", 1000);
            player.sendMessage("§aAdded Tier " + newTier + " (radius=25, price=1000). Edit values by clicking.");
            populate();
            player.updateInventory();
            return;
        }

        if (idx >= tierCount) return;
        int tierNum = idx + 1;
        boolean isShiftRight = click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
        if (isShiftRight) {
            int maxTier = plugin.getConfig().getConfigurationSection("tiers").getKeys(false).size();
            plugin.getConfig().set("tiers." + tierNum, null);
            for (int t = tierNum + 1; t <= maxTier; t++) {
                int r = plugin.getConfig().getInt("tiers." + t + ".radius");
                int p = plugin.getConfig().getInt("tiers." + t + ".price");
                plugin.getConfig().set("tiers." + (t - 1) + ".radius", r);
                plugin.getConfig().set("tiers." + (t - 1) + ".price", p);
            }
            plugin.getConfig().set("tiers." + maxTier, null);
            player.sendMessage("§cRemoved Tier " + tierNum + ". Higher tiers shifted down.");
            populate();
            player.updateInventory();
            return;
        }
        boolean isRight = click == org.bukkit.event.inventory.ClickType.RIGHT;
        String key = "tiers." + tierNum + "." + (isRight ? "price" : "radius");
        player.closeInventory();
        plugin.setPendingInput(player.getUniqueId(), "config:" + key);
        player.sendMessage("§eType new " + (isRight ? "price" : "radius") + " for Tier " + tierNum + " in chat, or type §c'cancel'§e.");
    }

    private int slotToIndex(int slot) {
        int idx = slot - 10;
        if (idx < 0) return -1;
        int rowOffset = idx / 9;
        return idx - rowOffset * 2;
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
                loreComponents.add(Component.text(line.replaceAll("&", "§")));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }
}
