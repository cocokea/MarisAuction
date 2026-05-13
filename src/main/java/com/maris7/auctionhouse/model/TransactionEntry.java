package com.maris7.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record TransactionEntry(
        long id,
        UUID owner,
        UUID counterparty,
        String counterpartyName,
        ItemStack item,
        double price,
        boolean purchase,
        long createdAt
) {
}
