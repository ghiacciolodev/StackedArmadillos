package dev.ghiacciolo.stackedarmadillos;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** A rectangle of a world, at every height, where nothing gets stacked. */
record NoStackArea(String name, String world, int minX, int minZ, int maxX, int maxZ) {

    boolean contains(Location location) {
        return location.getWorld() != null && location.getWorld().getName().equals(world)
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    String describe() {
        return name + " (" + world + " " + minX + " " + minZ + " to " + maxX + " " + maxZ + ")";
    }

    /** Reads the no-stack-areas section, skipping wrong entries and reporting them in warnings. */
    static List<NoStackArea> load(ConfigurationSection section, List<String> warnings) {
        List<NoStackArea> areas = new ArrayList<>();
        if (section == null) {
            return areas;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection area = section.getConfigurationSection(name);
            int[] from = area == null ? null : corner(area.getString("from"));
            int[] to = area == null ? null : corner(area.getString("to"));
            String world = area == null ? null : area.getString("world");
            if (world == null || from == null || to == null) {
                warnings.add("No-stack area '" + name + "' needs world, from and to, like from: -100 200. Skipped.");
                continue;
            }
            // The corners can be given in any order.
            areas.add(new NoStackArea(name, world, Math.min(from[0], to[0]), Math.min(from[1], to[1]),
                    Math.max(from[0], to[0]), Math.max(from[1], to[1])));
        }
        return areas;
    }

    /** Parses "x z" into two block coordinates, or null if it isn't two whole numbers. */
    private static int[] corner(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.trim().split("[\\s,]+");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
