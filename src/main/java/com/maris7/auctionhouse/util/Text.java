package com.maris7.auctionhouse.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Text() {}

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, ChatColor.COLOR_CHAR + "x"
                    + ChatColor.COLOR_CHAR + matcher.group(1).charAt(0)
                    + ChatColor.COLOR_CHAR + matcher.group(1).charAt(1)
                    + ChatColor.COLOR_CHAR + matcher.group(1).charAt(2)
                    + ChatColor.COLOR_CHAR + matcher.group(1).charAt(3)
                    + ChatColor.COLOR_CHAR + matcher.group(1).charAt(4)
                    + ChatColor.COLOR_CHAR + matcher.group(1).charAt(5));
        }
        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }

    public static Component component(String input) {
        return LEGACY.deserialize(color(input));
    }

    public static boolean isBlankMessage(String input) {
        if (input == null) {
            return true;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() || trimmed.equals("\"\"") || trimmed.equals("''") || trimmed.equals("[]");
    }

    public static void send(CommandSender sender, String input) {
        if (sender == null || isBlankMessage(input)) {
            return;
        }
        sender.sendMessage(color(input));
    }

    public static List<String> colors(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (isBlankMessage(line)) {
                continue;
            }
            out.add(color(line));
        }
        return out;
    }
}
