package com.maris7.auctionhouse.service;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class SoundService {

    private final MarisAuctionPlugin plugin;

    public SoundService(MarisAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String key) {
        if (player == null || !player.isOnline() || key == null || key.isBlank()) {
            return;
        }
        YamlConfiguration cfg = plugin.getConfigRegistry().get("sounds.yml");
        if (cfg.isConfigurationSection(key)) {
            if (!cfg.getBoolean(key + ".enabled", true)) {
                return;
            }
            Sound sound = parseSound(cfg.getString(key + ".sound"));
            if (sound == null) {
                return;
            }
            float volume = (float) cfg.getDouble(key + ".volume", 1.0D);
            float pitch = (float) cfg.getDouble(key + ".pitch", 1.0D);
            player.playSound(player.getLocation(), sound, volume, pitch);
            return;
        }

        Sound legacy = parseSound(cfg.getString(key));
        if (legacy != null) {
            player.playSound(player.getLocation(), legacy, 1.0F, 1.0F);
        }
    }

    private Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
