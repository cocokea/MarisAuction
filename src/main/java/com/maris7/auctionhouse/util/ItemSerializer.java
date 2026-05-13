package com.maris7.auctionhouse.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemSerializer {
    private ItemSerializer() {}

    public static String encode(ItemStack item) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
                data.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static ItemStack decode(String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
                return (ItemStack) input.readObject();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
