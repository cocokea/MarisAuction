package com.maris7.auctionhouse.service;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.util.FoliaScheduler;
import com.maris7.auctionhouse.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SignInputService {

    private final MarisAuctionPlugin plugin;
    private final boolean packetEventsAvailable;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SignInputService(MarisAuctionPlugin plugin, boolean packetEventsAvailable) {
        this.plugin = plugin;
        this.packetEventsAvailable = packetEventsAvailable;
    }

    public boolean isPacketEventsAvailable() {
        return packetEventsAvailable;
    }

    public void openSearch(Player player, Consumer<String> callback) {
        open(player, getConfiguredLines("sign.search", new String[]{"^^^^^^^^^^^^", "Search", "", ""}), callback);
    }

    public void openPrice(Player player, Consumer<String> callback) {
        open(player, getConfiguredLines("sign.price", new String[]{"", "Type Price", "^^^^^^^^^^^^", ""}), callback);
    }

    public boolean matches(UUID uniqueId, Location location) {
        Session session = sessions.get(uniqueId);
        if (session == null || location == null) {
            return false;
        }
        return session.location().getWorld() != null
                && session.location().getWorld().equals(location.getWorld())
                && session.location().getBlockX() == location.getBlockX()
                && session.location().getBlockY() == location.getBlockY()
                && session.location().getBlockZ() == location.getBlockZ();
    }

    public void complete(Player player, String[] lines) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        restore(session);

        Set<String> ignored = getIgnoredLines();
        String value = "";
        if (lines != null) {
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !ignored.contains(trimmed)) {
                    value = trimmed;
                    break;
                }
            }
        }
        session.callback().accept(value);
    }

    public void clear(UUID uniqueId) {
        Session removed = sessions.remove(uniqueId);
        if (removed != null) {
            restore(removed);
        }
    }

    public void clearAll() {
        for (UUID uniqueId : Set.copyOf(sessions.keySet())) {
            clear(uniqueId);
        }
    }

    private String[] getConfiguredLines(String path, String[] fallback) {
        List<String> configured = plugin.getConfigRegistry().messages().getStringList(path);
        String[] lines = fallback.clone();
        for (int index = 0; index < Math.min(4, configured.size()); index++) {
            lines[index] = configured.get(index);
        }
        return lines;
    }

    private void open(Player player, String[] lines, Consumer<String> callback) {
        Block block = findTemporaryBlock(player);
        Location location = block.getLocation();
        Material originalType = block.getType();
        org.bukkit.block.data.BlockData originalData = block.getBlockData().clone();

        FoliaScheduler.runRegion(plugin, location, () -> {
            World world = location.getWorld();
            if (world == null) {
                return;
            }
            Block target = world.getBlockAt(location);
            target.setType(Material.OAK_SIGN, false);
            if (!(target.getState() instanceof Sign sign)) {
                return;
            }

            SignSide side = sign.getSide(Side.FRONT);
            for (int index = 0; index < 4; index++) {
                side.line(index, Text.component(index < lines.length && lines[index] != null ? lines[index] : ""));
                sign.getSide(Side.BACK).line(index, Component.empty());
            }
            sign.setWaxed(false);
            trySetAllowedEditor(sign, player.getUniqueId());
            sign.update(true, false);
            sessions.put(player.getUniqueId(), new Session(location, originalType, originalData, callback));
            final String[] clientLines = colorLines(lines);
            FoliaScheduler.runEntity(plugin, player, () -> {
                if (!player.isOnline()) {
                    clear(player.getUniqueId());
                    return;
                }
                player.sendSignChange(location, clientLines);
                FoliaScheduler.runEntityLater(plugin, player, () -> {
                    if (!player.isOnline()) {
                        clear(player.getUniqueId());
                        return;
                    }
                    World w = location.getWorld();
                    if (w == null) {
                        return;
                    }
                    Block at = w.getBlockAt(location);
                    if (!(at.getState() instanceof Sign openSign)) {
                        return;
                    }
                    player.openSign(openSign, Side.FRONT);
                }, 2L);
            });
        });
    }

    private String[] colorLines(String[] lines) {
        String[] colored = new String[4];
        for (int index = 0; index < colored.length; index++) {
            colored[index] = Text.color(index < lines.length && lines[index] != null ? lines[index] : "");
        }
        return colored;
    }

    private Set<String> getIgnoredLines() {
        Set<String> ignored = new LinkedHashSet<>();
        ignored.addAll(Arrays.asList(getConfiguredLines("sign.search", new String[]{"^^^^^^^^^^^^", "Search", "", ""})));
        ignored.addAll(Arrays.asList(getConfiguredLines("sign.price", new String[]{"", "Type Price", "^^^^^^^^^^^^", ""})));
        ignored.addAll(plugin.getConfigRegistry().messages().getStringList("sign.ignored-lines"));
        ignored.removeIf(line -> line == null || line.isBlank());
        return ignored;
    }

    private void restore(Session session) {
        FoliaScheduler.runRegion(plugin, session.location(), () -> {
            World world = session.location().getWorld();
            if (world == null) {
                return;
            }
            Block block = world.getBlockAt(session.location());
            block.setType(session.originalType(), false);
            try {
                block.setBlockData(session.originalData().clone(), false);
            } catch (Throwable ignored) {
            }
        });
    }

    private Block findTemporaryBlock(Player player) {
        Location head = player.getEyeLocation().clone().add(0.0D, 1.0D, 0.0D);
        return head.getBlock();
    }

    private void trySetAllowedEditor(Sign sign, UUID uuid) {
        try {
            Method method = sign.getClass().getMethod("setAllowedEditorUniqueId", UUID.class);
            method.invoke(sign, uuid);
        } catch (Throwable ignored) {
        }
    }

    public record Session(Location location,
                          Material originalType,
                          org.bukkit.block.data.BlockData originalData,
                          Consumer<String> callback) {}
}
