package com.maris7.auctionhouse.util;

import com.maris7.auctionhouse.config.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ItemUtil {
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)(?:§[0-9A-FK-ORX]|&#[0-9A-F]{6}|&#[0-9A-F]{6}|&[0-9A-FK-OR])");
    private static final Map<Character, String> SEARCH_CHAR_MAP = createSearchCharMap();

    private ItemUtil() {}

    public static String resolveName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                Object hasItemName = meta.getClass().getMethod("hasItemName").invoke(meta);
                if (Boolean.TRUE.equals(hasItemName)) {
                    Object itemName = meta.getClass().getMethod("getItemName").invoke(meta);
                    if (itemName instanceof String string && !string.isBlank()) {
                        return string;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
            if (meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
        }
        return prettify(item.getType().name());
    }

    public static boolean isBlacklisted(ConfigRegistry configRegistry, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        List<String> entries = configRegistry.get("config.yml").getStringList("blacklist-items");
        String materialName = item.getType().name().toUpperCase(Locale.ROOT);
        for (String entry : entries) {
            if (entry != null && materialName.equals(entry.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> detailLore(ItemStack item) {
        List<String> lore = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return lore;
        }
        if (meta.hasLore() && meta.getLore() != null) {
            lore.addAll(meta.getLore());
        }
        if (meta.hasAttributeModifiers() && meta.getAttributeModifiers() != null) {
            for (var entry : meta.getAttributeModifiers().asMap().entrySet()) {
                for (AttributeModifier modifier : entry.getValue()) {
                    lore.add(Text.color("&8" + prettify(entry.getKey().name()) + " " + trimModifier(modifier.getAmount())));
                }
            }
        }
        return lore;
    }

    private static String trimModifier(double value) {
        String raw = String.format(Locale.US, "%.2f", value);
        while (raw.contains(".") && (raw.endsWith("0") || raw.endsWith("."))) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    public static boolean isShulker(ItemStack item) {
        return item.getItemMeta() instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox;
    }

    public static ItemStack[] shulkerContents(ItemStack item) {
        if (!(item.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return new ItemStack[27];
        }
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return new ItemStack[27];
        }
        ItemStack[] contents = shulkerBox.getInventory().getContents();
        return contents == null ? new ItemStack[27] : contents;
    }

    public static String categoryOf(ItemStack item) {
        Material type = item.getType();
        String name = type.name();
        if (type.isBlock()) {
            return "BLOCKS";
        }
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("SWORD") || name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT")) {
            return "COMBAT";
        }
        if (type.getMaxDurability() > 0 || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_HOE") || name.endsWith("_SHOVEL")) {
            return "TOOLS";
        }
        if (name.contains("POTION") || name.contains("BREWING")) {
            return "POTIONS";
        }
        if (name.contains("BOOK") || name.contains("ENCHANTED_BOOK")) {
            return "BOOKS";
        }
        if (name.contains("APPLE") || name.contains("BEEF") || name.contains("BREAD") || name.contains("PORK") || name.contains("CARROT") || name.contains("POTATO") || name.contains("FISH") || name.contains("COOKED") || name.contains("STEW") || name.contains("COOKIE") || name.contains("MELON") || name.contains("PUMPKIN_PIE")) {
            return "FOOD";
        }
        if (name.contains("INGOT") || name.contains("NUGGET") || name.contains("GEM") || name.contains("DUST") || name.contains("SHARD") || name.contains("CRYSTAL") || name.contains("STRING") || name.contains("LEATHER") || name.contains("BLAZE_ROD") || name.contains("PEARL") || name.contains("EYE")) {
            return "INGREDIENTS";
        }
        return "UTILITIES";
    }

    public static String normalizeSearchText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String stripped = COLOR_PATTERN.matcher(input).replaceAll("");
        StringBuilder out = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char character = Character.toLowerCase(stripped.charAt(i));
            String mapped = SEARCH_CHAR_MAP.get(character);
            if (mapped != null) {
                out.append(mapped);
                continue;
            }
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                out.append(character);
            } else if (Character.isWhitespace(character) || character == '_' || character == '-') {
                out.append(' ');
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    private static Map<Character, String> createSearchCharMap() {
        Map<Character, String> map = new HashMap<>();
        map.put('ᴀ', "a");
        map.put('ʙ', "b");
        map.put('ᴄ', "c");
        map.put('ᴅ', "d");
        map.put('ᴇ', "e");
        map.put('ꜰ', "f");
        map.put('ɢ', "g");
        map.put('ʜ', "h");
        map.put('ɪ', "i");
        map.put('ᴊ', "j");
        map.put('ᴋ', "k");
        map.put('ʟ', "l");
        map.put('ᴍ', "m");
        map.put('ɴ', "n");
        map.put('ᴏ', "o");
        map.put('ᴘ', "p");
        map.put('ǫ', "q");
        map.put('ʀ', "r");
        map.put('s', "s");
        map.put('ᴛ', "t");
        map.put('ᴜ', "u");
        map.put('ᴠ', "v");
        map.put('ᴡ', "w");
        map.put('x', "x");
        map.put('ʏ', "y");
        map.put('ᴢ', "z");
        return map;
    }

    private static String prettify(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] split = lower.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : split) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return out.toString().trim();
    }
}
