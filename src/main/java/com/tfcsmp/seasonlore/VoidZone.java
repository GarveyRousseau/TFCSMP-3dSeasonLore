package com.tfcsmp.seasonlore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record VoidZone(String worldName, int x, int y, int z, int radius) {
    public Location center() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public boolean contains(Location location) {
        return location.getWorld() != null
            && location.getWorld().getName().equals(worldName)
            && location.distanceSquared(new Location(location.getWorld(), x, y, z)) <= (double) radius * radius;
    }

    public static VoidZone from(ConfigurationSection section) {
        return new VoidZone(
            section.getString("world", "world"),
            section.getInt("x"),
            section.getInt("y"),
            section.getInt("z"),
            section.getInt("radius", 48)
        );
    }
}
