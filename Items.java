package dev.vupe.core.util;

import dev.vupe.core.VupeCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class Items {
    private Items() {}

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.component(name));
        if (lore != null) meta.lore(lore.stream().map(Text::component).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack tagged(Material material, String name, List<String> lore, String key, String value) {
        ItemStack stack = item(material, name, lore);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(VupeCore.get(), key), PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        return stack;
    }

    public static String tag(ItemStack stack, String key) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(VupeCore.get(), key), PersistentDataType.STRING);
    }

    public static boolean hasTag(ItemStack stack, String key, String expected) {
        String value = tag(stack, key);
        return value != null && value.equalsIgnoreCase(expected);
    }
}
