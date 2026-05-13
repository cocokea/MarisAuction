package com.maris7.auctionhouse.util;

import org.bukkit.entity.Player;

public final class PermissionUtil {
    private PermissionUtil() {}

    public static int getSellLimit(Player player) {
        if (player.hasPermission("marisauction.sell.unlimited") || player.hasPermission("marisauction.sell.*")) {
            return -1;
        }

        int max = 0;
        for (int i = 1; i <= 25; i++) {
            if (player.hasPermission("marisauction.sell." + i)) {
                max = i;
            }
        }
        return max > 0 ? max : -1;
    }
}
