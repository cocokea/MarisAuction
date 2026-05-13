package com.maris7.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record ClaimEntry(
        long id,
        UUID owner,
        ItemStack item,
        String reason,
        long createdAt
) {
}
