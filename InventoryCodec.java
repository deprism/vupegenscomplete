package dev.vupe.core.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public final class InventoryCodec {
    private InventoryCodec() {}

    public static String encode(Inventory inventory) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeInt(inventory.getSize());
                for (ItemStack item : inventory.getContents()) out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not encode inventory", ex);
        }
    }

    public static void decodeInto(String encoded, Inventory inventory) {
        if (encoded == null || encoded.isBlank()) return;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int size = in.readInt();
                for (int slot = 0; slot < Math.min(size, inventory.getSize()); slot++) {
                    inventory.setItem(slot, (ItemStack) in.readObject());
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decode inventory", ex);
        }
    }

    public static String encodeItem(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not encode item", ex);
        }
    }

    public static ItemStack decodeItem(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (ItemStack) in.readObject();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decode item", ex);
        }
    }
}
