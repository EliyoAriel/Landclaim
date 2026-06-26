package com.landclaim.util;

import com.landclaim.LandClaimPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class MessageManager {

    private final LandClaimPlugin plugin;
    private FileConfiguration messages;
    private FileConfiguration defaults;

    public MessageManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
        saveDefaults();
        this.messages = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "messages.yml"));
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            this.defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
        }
    }

    private void saveDefaults() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        } else {
            mergeDefaults(file);
        }
    }

    private void mergeDefaults(File file) {
        FileConfiguration existing = YamlConfiguration.loadConfiguration(file);
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream == null) return;
        FileConfiguration def = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defStream, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (!existing.contains(key)) {
                existing.set(key, def.get(key));
                changed = true;
            }
        }
        if (changed) {
            try {
                existing.save(file);
                plugin.getLogger().info("Updated messages.yml with new keys.");
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save updated messages.yml: " + e.getMessage());
            }
        }
    }

    private String getMessage(String path) {
        String msg = messages.getString(path);
        if (msg != null && !msg.isEmpty()) return msg;
        if (defaults != null) {
            msg = defaults.getString(path);
            if (msg != null && !msg.isEmpty()) return msg;
        }
        return "&cMissing message: " + path;
    }

    private String getPrefix() {
        String p = messages.getString("prefix");
        if (p == null || p.isEmpty()) p = "&7[&aLandClaim&7]";
        return p;
    }

    public void reload() {
        this.messages = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "messages.yml"));
    }

    public String raw(String path) {
        String prefix = getPrefix();
        return (prefix + " &r" + getMessage(path)).replace("&", "§");
    }

    public String raw(String path, Map<String, String> placeholders) {
        String msg = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        String prefix = getPrefix();
        return (prefix + " &r" + msg).replace("&", "§");
    }

    public String rawNoPrefix(String path) {
        return getMessage(path).replace("&", "§");
    }

    public String rawNoPrefix(String path, Map<String, String> placeholders) {
        String msg = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return msg.replace("&", "§");
    }

    public Component text(String path) {
        return Component.text(raw(path));
    }

    public Component text(String path, Map<String, String> placeholders) {
        return Component.text(raw(path, placeholders));
    }

    public Component textNoPrefix(String path) {
        return Component.text(rawNoPrefix(path));
    }

    public Component textNoPrefix(String path, Map<String, String> placeholders) {
        return Component.text(rawNoPrefix(path, placeholders));
    }

    public Title title(String titlePath, String subtitlePath) {
        return title(titlePath, subtitlePath, null);
    }

    public Title title(String titlePath, String subtitlePath, Map<String, String> placeholders) {
        String t = rawNoPrefix(titlePath, placeholders);
        String s = rawNoPrefix(subtitlePath, placeholders);
        return Title.title(
                Component.text(t),
                Component.text(s),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
        );
    }
}
