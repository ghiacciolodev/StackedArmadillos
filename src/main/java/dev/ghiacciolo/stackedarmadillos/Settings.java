package dev.ghiacciolo.stackedarmadillos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

/** The values of config.yml, read once on load and on every reload. */
record Settings(
        Set<String> worlds,
        int maxStackSize,
        boolean sameBlockOnly,
        boolean infestedEnabled,
        double infestedChance,
        int minSilverfish,
        int maxSilverfish,
        boolean crammingEnabled,
        int crammingLimit,
        double crammingDamage,
        int crammingInterval,
        boolean scutesEnabled) {

    /** Reads the config, fixing values that make no sense and reporting them in warnings. */
    static Settings load(FileConfiguration config, List<String> warnings) {
        int maxStackSize = config.getInt("max-stack-size", 24);
        if (maxStackSize < 0) {
            warnings.add("max-stack-size can't be negative, using 0 (no limit).");
            maxStackSize = 0;
        }

        double chance = config.getDouble("infested.chance", 0.1);
        if (chance < 0 || chance > 1) {
            warnings.add("infested.chance must be between 0 and 1, using 0.1.");
            chance = 0.1;
        }
        int minSilverfish = Math.max(0, config.getInt("infested.min-silverfish", 1));
        int maxSilverfish = config.getInt("infested.max-silverfish", 2);
        if (maxSilverfish < minSilverfish) {
            warnings.add("infested.max-silverfish is lower than min-silverfish, using " + minSilverfish + ".");
            maxSilverfish = minSilverfish;
        }

        int interval = config.getInt("cramming.interval-ticks", 4);
        if (interval < 1) {
            warnings.add("cramming.interval-ticks must be at least 1, using 4.");
            interval = 4;
        }

        return new Settings(
                new HashSet<>(config.getStringList("worlds")),
                maxStackSize,
                config.getBoolean("same-block-only", true),
                config.getBoolean("infested.enabled", true),
                chance,
                minSilverfish,
                maxSilverfish,
                config.getBoolean("cramming.enabled", true),
                config.getInt("cramming.limit", -1),
                Math.max(0, config.getDouble("cramming.damage", 6.0)),
                interval,
                config.getBoolean("scutes.enabled", true));
    }

    boolean isActiveIn(World world) {
        return worlds.isEmpty() || worlds.contains(world.getName());
    }

    String describe() {
        return "Active in " + (worlds.isEmpty() ? "all worlds" : String.join(", ", worlds))
                + ", max stack size: " + (maxStackSize == 0 ? "no limit" : maxStackSize)
                + ", same block only: " + onOff(sameBlockOnly)
                + ", infested: " + onOff(infestedEnabled)
                + ", cramming: " + (crammingEnabled ? (crammingLimit < 0 ? "gamerule" : crammingLimit) : "off")
                + ", scutes: " + onOff(scutesEnabled) + ".";
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
