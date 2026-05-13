package com.maris7.auctionhouse.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConfigRegistry {

    private static final List<String> RESOURCE_PATHS = List.of(
            "config.yml",
            "sounds.yml",
            "message/message_en.yml",
            "message/message_vi.yml",
            "guis/en/auction-main.yml",
            "guis/en/filter-menu.yml",
            "guis/en/sort-menu.yml",
            "guis/en/your-items.yml",
            "guis/en/insert-item.yml",
            "guis/en/confirm-listing.yml",
            "guis/en/buy-confirm.yml",
            "guis/en/transactions.yml",
            "guis/en/shulker-preview.yml",
            "guis/vi/auction-main.yml",
            "guis/vi/filter-menu.yml",
            "guis/vi/sort-menu.yml",
            "guis/vi/your-items.yml",
            "guis/vi/insert-item.yml",
            "guis/vi/confirm-listing.yml",
            "guis/vi/buy-confirm.yml",
            "guis/vi/transactions.yml",
            "guis/vi/shulker-preview.yml"
    );

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> cache = new HashMap<>();

    public ConfigRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        for (String path : RESOURCE_PATHS) {
            load(path);
        }
    }

    public YamlConfiguration get(String path) {
        return Objects.requireNonNull(cache.get(path), "Missing config: " + path);
    }

    private void load(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            plugin.saveResource(path, false);
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(path)), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!current.contains(key)) {
                    current.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                current.save(file);
            }
            cache.put(path, current);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load config " + path, ex);
        }
    }

    public Lang messages() {
        String locale = get("config.yml").getString("language", "en");
        return new Lang(get("message/message_" + locale + ".yml"));
    }

    public YamlConfiguration gui(String name) {
        String locale = get("config.yml").getString("language", "en");
        return get("guis/" + locale + "/" + name + ".yml");
    }

    public record Lang(YamlConfiguration config) {
        public String get(String path) {
            if (!config.contains(path)) {
                return path;
            }
            Object value = config.get(path);
            if (value == null) {
                return "";
            }
            if (value instanceof String string) {
                return string;
            }
            if (value instanceof java.util.List<?> list && list.isEmpty()) {
                return "";
            }
            return String.valueOf(value);
        }

        public java.util.List<String> getStringList(String path) {
            return config.getStringList(path);
        }
    }
}
