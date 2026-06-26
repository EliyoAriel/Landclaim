package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimManager;
import com.landclaim.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.UUID;

public class ClaimGUI implements Listener {

    private final LandClaimPlugin plugin;
    private final Player player;
    private final Claim claim;
    private final ClaimManager claimManager;
    private final EconomyManager economyManager;
    private Inventory inventory;

    public ClaimGUI(LandClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.claim = plugin.getClaimManager().getClaim(player.getUniqueId());
        this.claimManager = plugin.getClaimManager();
        this.economyManager = plugin.getEconomyManager();
    }

    public void open() {
        String title = plugin.getConfig().getString("gui.title", "&8&lClaim Management");
        int rows = plugin.getConfig().getInt("gui.rows", 3);
        this.inventory = Bukkit.createInventory(null, rows * 9, Component.text(title.replaceAll("&", "§")));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        fillBorder();

        String nameDisplay = claim.getName() != null ? claim.getName() : "Unnamed";
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7» Tier: §f" + claim.getTier());
        infoLore.add("§7» Radius: §f" + claim.getRadius());
        infoLore.add("§7» Center: §f" + claim.getCenterX() + ", " + claim.getCenterZ());
        if (plugin.getConfig().getBoolean("tax.enabled", false)) {
            List<Integer> costs = plugin.getConfig().getIntegerList("tax.cost-per-tier");
            int defaultRadius = plugin.getConfig().getInt("default-radius", 5);
            int baseCost = claim.getTier() <= costs.size() ? costs.get(claim.getTier() - 1) : 0;
            if (baseCost > 0) {
                int taxCost = (int) Math.round((double) baseCost * claim.getRadius() / defaultRadius);
                long now = System.currentTimeMillis();
                long nextDue = claim.getTaxNextDue();
                long graceEnd = claim.getTaxGraceEnd();
                String dueStr = nextDue > 0 ? formatTime(nextDue - now)
                        : "§7~" + plugin.getConfig().getInt("tax.interval-hours", 24) + "h";
                String status;
                if (claim.isTaxDelinquent()) {
                    status = "§c§lDELINQUENT";
                } else if (graceEnd > 0 && now > nextDue) {
                    status = "§e§lGRACE";
                } else {
                    status = "§a§lOK";
                }
                infoLore.add("");
                infoLore.add("§7» Tax: §f$" + taxCost + " §8(" + status + "§8)");
                infoLore.add("§7» Next due: §f" + dueStr);
            }
        }
        infoLore.add("");
        infoLore.add("§eClick to teleport");
        ItemStack info = createItem(Material.MAP, "§b§l" + nameDisplay,
                infoLore.toArray(new String[0]));
        inventory.setItem(11, info);

        ItemStack flags = createItem(Material.COMPARATOR, "§6§l⚙ Claim Flags",
                "§7Configure PVP, mobs, fire,",
                "§7explosions, griefing & more",
                "",
                "§eClick to manage");
        inventory.setItem(12, flags);

        if (claimManager.canUpgrade(player)) {
            int cost = claimManager.getUpgradeCost(player);
            boolean canAfford = economyManager.hasBalance(player, cost);
            Material mat = canAfford ? Material.EXPERIENCE_BOTTLE : Material.BARRIER;
            String name = canAfford ? "§a§l⬆ Upgrade Claim" : "§c§l✖ Cannot Afford";
            int nextTier = claim.getTier() + 1;
            int nextRadius = plugin.getConfig().getInt("tiers." + nextTier + ".radius", claim.getRadius() + 25);
            String costStr = "$" + cost;
            ItemStack upgrade = createItem(mat, name,
                    "§7» Next Tier: §f" + nextTier,
                    "§7» New Radius: §f" + nextRadius,
                    "§7» Cost: §f" + costStr,
                    "§7» Balance: §f$" + economyManager.getBalance(player));
            inventory.setItem(13, upgrade);
        } else {
            ItemStack maxed = createItem(Material.NETHER_STAR, "§6§l★ MAX TIER",
                    "§7Your claim is fully upgraded!");
            inventory.setItem(13, maxed);
        }

        int trustedCount = claim.getAllowedPlayers().size();
        ItemStack trust = createItem(Material.NAME_TAG, "§d§l👥 Trusted Players",
                "§7» Trusted: §f" + trustedCount,
                "",
                "§eClick to manage");
        inventory.setItem(14, trust);

        boolean borderShowing = plugin.getClaimBorder().isShowing(player.getUniqueId());
        ItemStack borderItem = createItem(
                borderShowing ? Material.ENDER_EYE : Material.ENDER_PEARL,
                (borderShowing ? "§a§l" : "§7§l") + "◈ Border",
                "§7Click to " + (borderShowing ? "disable" : "enable") + " claim border");
        inventory.setItem(15, borderItem);

        ItemStack setspawn = createItem(Material.ENDER_PEARL, "§b§l⬇ Set Spawn",
                "§7Set your claim teleport location",
                "§7to where you're standing",
                "",
                "§eClick to set");
        inventory.setItem(16, setspawn);
    }

    private void fillBorder() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }
    }

    private ItemStack createItem(Material material, String name, String... lore) {
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

    private String formatTime(long millis) {
        if (millis <= 0) return "§cOverdue";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        if (slot == 11) {
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
            player.closeInventory();
            Location spawn = claim.getSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            } else {
                player.teleport(new Location(claim.getWorld(), claim.getCenterX() + 0.5, player.getWorld().getHighestBlockYAt(claim.getCenterX(), claim.getCenterZ()) + 1, claim.getCenterZ() + 0.5));
            }
            plugin.getHomeCooldowns().put(pid, now);
            player.sendMessage(plugin.getMsg().text("claim.home.success"));
        } else if (slot == 12) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new FlagGUI(plugin, player).open();
            }, 2L);
        } else if (slot == 13 && claimManager.canUpgrade(player)) {
            int cost = claimManager.getUpgradeCost(player);
            if (economyManager.hasBalance(player, cost)) {
                if (!claimManager.upgrade(player)) {
                    if (claimManager.collidesWithAny(claim)) {
                        player.sendMessage(plugin.getMsg().text("claim.upgrade.gui_collision"));
                    }
                    return;
                }
                economyManager.withdraw(player, cost);
                player.closeInventory();
                player.sendMessage(plugin.getMsg().text("claim.upgrade.success", Map.of("tier", String.valueOf(claim.getTier()))));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ClaimGUI refreshed = new ClaimGUI(plugin, player);
                    refreshed.open();
                }, 2L);
            } else {
                player.sendMessage(plugin.getMsg().text("claim.upgrade.gui_no_funds"));
            }
        } else if (slot == 14) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new TrustGUI(plugin, player).open();
            }, 2L);
        } else if (slot == 16) {
            player.closeInventory();
            Location loc = player.getLocation();
            if (!claim.contains(loc)) {
                player.sendMessage(plugin.getMsg().text("claim.setspawn.outside"));
                return;
            }
            claim.setSpawnLocation(loc);
            claimManager.saveAll();
            player.sendMessage(plugin.getMsg().text("claim.setspawn.success"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new ClaimGUI(plugin, player).open();
            }, 2L);
        } else if (slot == 15) {
            player.closeInventory();
            plugin.getClaimBorder().toggle(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ClaimGUI refreshed = new ClaimGUI(plugin, player);
                refreshed.open();
            }, 2L);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
