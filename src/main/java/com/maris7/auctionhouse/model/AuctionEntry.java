package com.maris7.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record AuctionEntry(
        long id,
        UUID seller,
        String sellerName,
        ItemStack item,
        double price,
        long expiresAt,
        String searchKey,
        String category,
        boolean sold,
        UUID buyer,
        String buyerName
) {
}
