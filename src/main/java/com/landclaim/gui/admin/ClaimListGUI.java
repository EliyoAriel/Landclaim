package com.landclaim.gui.admin;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
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

public class ClaimListGUI implements Listener {

    private static final int ROWS = 5;
    private static final int CLAIMS_PER_PAGE = 18;

    private final LandClaimPlugin plugin;
    private final Player player;
    private final int page;
    private Inventory inventory;

    public ClaimListGUI(LandClaimPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(null, ROWS * 9,
                Component.text("§8§lClaims (Page " + (page + 1) + ")"));
        populate();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void populate() {
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        List<Claim> allClaims = new ArrayList<>(plugin.getClaimManager().getAllClaims());
        int totalPages = Math.max(1, (int) Math.ceil((double) allClaims.size() / CLAIMS_PER_PAGE));
        int start = page * CLAIMS_PER_PAGE;
        int end = Math.min(start + CLAIMS_PER_PAGE, allClaims.size());

        int slot = 9;
        for (int i = start; i < end; i++) {
            Claim claim = allClaims.get(i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());
            String name = owner.getName() != null ? owner.getName() : claim.getOwner().toString().substring(0, 8);

            int trusted = claim.getAllowedPlayers().size();
            List<String> lore = new ArrayList<>();
            lore.add("§7Tier: §f" + claim.getTier() + " §7| Radius: §f" + claim.getRadius());
            lore.add("§7Location: §f" + claim.getCenterX() + ", " + claim.getCenterZ() + " (§f" + claim.getWorld().getName() + "§7)");
            lore.add("§7Trusted: §f" + trusted);
            lore.add("");
            lore.add("§eLeft-click to teleport");
            lore.add("§cRight-click to remove");

            ItemStack head = getHead(owner, "§e" + name, lore.toArray(new String[0]));
            inventory.setItem(slot++, head);
        }

        if (page > 0) {
            inventory.setItem(36, createItem(Material.ARROW, "§e§lPrevious Page",
                    "§7Page " + page));
        }
        inventory.setItem(40, createItem(Material.PAPER, "§6§lPage " + (page + 1) + "/" + totalPages));
        if (page < totalPages - 1) {
            inventory.setItem(44, createItem(Material.ARROW, "§e§lNext Page",
                    "§7Page " + (page + 2)));
        }
        inventory.setItem(38, createItem(Material.REDSTONE, "§c§lBack",
                "§7Return to admin panel"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();

        if (slot == 38) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new AdminGUI(plugin, player).open();
            }, 2L);
            return;
        }
        if (slot == 36 && page > 0) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new ClaimListGUI(plugin, player, page - 1).open();
            }, 2L);
            return;
        }
        if (slot == 44) {
            List<Claim> allClaims = new ArrayList<>(plugin.getClaimManager().getAllClaims());
            int totalPages = (int) Math.ceil((double) allClaims.size() / CLAIMS_PER_PAGE);
            if (page < totalPages - 1) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    new ClaimListGUI(plugin, player, page + 1).open();
                }, 2L);
            }
            return;
        }

        if (slot >= 9 && slot <= 35) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != Material.PLAYER_HEAD) return;
            if (!(item.getItemMeta() instanceof SkullMeta meta)) return;
            OfflinePlayer target = meta.getOwningPlayer();
            if (target == null || target.getUniqueId() == null) return;

            if (event.isRightClick()) {
                String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    new ConfirmGUI(plugin, player, "Remove " + targetName + "?",
                            () -> {
                                plugin.getClaimManager().removeClaim(target.getUniqueId());
                                player.sendMessage(plugin.getMsg().text("gui.claim_list.removed", Map.of("target", targetName)));
                                List<Claim> remaining = new ArrayList<>(plugin.getClaimManager().getAllClaims());
                                int lastPage = Math.max(0, (int) Math.ceil((double) remaining.size() / CLAIMS_PER_PAGE) - 1);
                                int newPage = Math.min(page, lastPage);
                                new ClaimListGUI(plugin, player, newPage).open();
                            },
                            () -> new ClaimListGUI(plugin, player, page).open()
                    ).open();
                }, 2L);
                return;
            }

            if (event.isLeftClick()) {
                com.landclaim.claim.Claim claim = plugin.getClaimManager().getClaim(target.getUniqueId());
                if (claim != null) {
                    player.closeInventory();
                    org.bukkit.Location spawn = claim.getSpawnLocation();
                    if (spawn != null) {
                        player.teleport(spawn);
                    } else {
                        player.teleport(new org.bukkit.Location(claim.getWorld(), claim.getCenterX() + 0.5,
                                claim.getWorld().getHighestBlockYAt(claim.getCenterX(), claim.getCenterZ()) + 1,
                                claim.getCenterZ() + 0.5));
                    }
                    player.sendMessage(plugin.getMsg().text("gui.claim_list.teleported", Map.of("target", target.getName())));
                }
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    private ItemStack getHead(OfflinePlayer offline, String name, String... lore) {
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

    private static ItemStack createItem(Material material, String name, String... lore) {
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
