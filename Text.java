package dev.vupe.core.util;

import dev.vupe.core.VupeCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.util.Map;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##");

    private Text() {}

    public static Component component(String input) {
        return MM.deserialize(input == null ? "" : input);
    }

    public static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(component(prefix() + miniMessage));
    }

    public static void raw(CommandSender sender, String miniMessage) {
        sender.sendMessage(component(miniMessage));
    }

    public static String prefix() {
        return VupeCore.get().configs().get("branding").getString(
            "brand.prefix",
            "<dark_gray>[<#8B5CF6><bold>VUPE</bold><dark_gray>] <gray>"
        );
    }

    public static String format(double value) {
        if (Math.abs(value) < 1000) return NUMBER.format(value);
        String[] suffix = {"", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp"};
        double n = value;
        int i = 0;
        while (Math.abs(n) >= 1000 && i < suffix.length - 1) {
            n /= 1000.0;
            i++;
        }
        return NUMBER.format(n) + suffix[i];
    }

    public static String replace(String template, Map<String, String> values) {
        String out = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return out;
    }

}
