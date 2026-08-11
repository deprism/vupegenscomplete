package dev.vupe.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {
    private Locations() {}

    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return String.join(";",
            loc.getWorld().getName(),
            Double.toString(loc.getX()),
            Double.toString(loc.getY()),
            Double.toString(loc.getZ()),
            Float.toString(loc.getYaw()),
            Float.toString(loc.getPitch())
        );
    }

    public static Location deserialize(String value) {
        if (value == null || value.isBlank()) return null;
        String[] p = value.split(";");
        if (p.length < 4) return null;
        World world = Bukkit.getWorld(p[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(p[1]);
            double y = Double.parseDouble(p[2]);
            double z = Double.parseDouble(p[3]);
            float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String blockKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
