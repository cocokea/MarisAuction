package com.maris7.auctionhouse.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class MoneyUtil {
    private MoneyUtil() {}

    public static double parseShort(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        String value = raw.trim().replace("$", "").replace(",", "").toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Price cannot be blank");
        }
        double multiplier = 1D;
        if (value.endsWith("K")) {
            multiplier = 1_000D;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("M")) {
            multiplier = 1_000_000D;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("B")) {
            multiplier = 1_000_000_000D;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("T")) {
            multiplier = 1_000_000_000_000D;
            value = value.substring(0, value.length() - 1);
        }
        double parsed = Double.parseDouble(value) * multiplier;
        if (!Double.isFinite(parsed) || parsed <= 0D) {
            throw new IllegalArgumentException("Price must be positive");
        }
        return parsed;
    }

    public static String formatShort(double amount) {
        double absolute = Math.abs(amount);
        String suffix = "";
        double display = amount;
        if (absolute >= 1_000_000_000_000D) {
            display = amount / 1_000_000_000_000D;
            suffix = "T";
        } else if (absolute >= 1_000_000_000D) {
            display = amount / 1_000_000_000D;
            suffix = "B";
        } else if (absolute >= 1_000_000D) {
            display = amount / 1_000_000D;
            suffix = "M";
        } else if (absolute >= 1_000D) {
            display = amount / 1_000D;
            suffix = "K";
        }
        return trim(display) + suffix;
    }

    private static String trim(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
