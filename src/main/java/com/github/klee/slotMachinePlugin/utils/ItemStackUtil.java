package com.github.klee.slotMachinePlugin.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ItemStackUtil {

    /**
     * ItemStack → Base64 文字列に変換
     */
    public static String itemToBase64(ItemStack item) throws IOException {
        if (item == null) throw new IllegalArgumentException("item cannot be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Base64 文字列 → ItemStack 復元
     */
    public static ItemStack itemFromBase64(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        }
    }
}
