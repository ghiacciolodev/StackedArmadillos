package dev.ghiacciolo.stackedarmadillos;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** The /sa area subcommands, to manage no-stack areas from the game. */
final class AreaCommand {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> SUBCOMMANDS = List.of("pos1", "pos2", "add", "remove", "list", "here");

    private final StackedArmadillos plugin;
    private final Supplier<Settings> settings;
    // Corners picked with pos1 and pos2, per player, until the next restart.
    private final Map<UUID, Location[]> corners = new HashMap<>();

    AreaCommand(StackedArmadillos plugin, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    void run(CommandSender sender, String label, String[] args) {
        String usage = "Usage: /" + label + " area <pos1|pos2|add <name>|remove <name>|list|here>";
        if (args.length < 2) {
            sender.sendMessage(usage);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pos1" -> setCorner(sender, 0);
            case "pos2" -> setCorner(sender, 1);
            case "add" -> add(sender, label, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            case "here" -> here(sender);
            default -> sender.sendMessage(usage);
        }
    }

    private void setCorner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can pick corners. From the console use: area add <name> <world> <x1> <z1> <x2> <z2>");
            return;
        }
        Location location = player.getLocation();
        corners.computeIfAbsent(player.getUniqueId(), id -> new Location[2])[index] = location;
        sender.sendMessage("Corner " + (index + 1) + " set at " + location.getBlockX() + " " + location.getBlockZ()
                + " in " + location.getWorld().getName() + ".");
    }

    private void add(CommandSender sender, String label, String[] args) {
        if (args.length != 3 && args.length != 8) {
            sender.sendMessage("Usage: /" + label + " area add <name>, after pos1 and pos2, "
                    + "or /" + label + " area add <name> <world> <x1> <z1> <x2> <z2>");
            return;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            sender.sendMessage("Use only lowercase letters, numbers, - and _ in the name, at most 32 characters.");
            return;
        }
        if (findArea(name) != null) {
            sender.sendMessage("An area called " + name + " already exists. Remove it first or pick another name.");
            return;
        }

        String world;
        int x1;
        int z1;
        int x2;
        int z2;
        if (args.length == 8) {
            world = args[3];
            try {
                x1 = Integer.parseInt(args[4]);
                z1 = Integer.parseInt(args[5]);
                x2 = Integer.parseInt(args[6]);
                z2 = Integer.parseInt(args[7]);
            } catch (NumberFormatException e) {
                sender.sendMessage("The coordinates must be whole numbers.");
                return;
            }
        } else {
            Location[] picked = sender instanceof Player player ? corners.get(player.getUniqueId()) : null;
            if (picked == null || picked[0] == null || picked[1] == null) {
                sender.sendMessage("Set both corners first with /" + label + " area pos1 and /" + label + " area pos2.");
                return;
            }
            if (!picked[0].getWorld().equals(picked[1].getWorld())) {
                sender.sendMessage("The two corners are in different worlds.");
                return;
            }
            world = picked[0].getWorld().getName();
            x1 = picked[0].getBlockX();
            z1 = picked[0].getBlockZ();
            x2 = picked[1].getBlockX();
            z2 = picked[1].getBlockZ();
        }
        if (plugin.getServer().getWorld(world) == null) {
            sender.sendMessage("World " + world + " is not loaded. Check the name, it is case sensitive.");
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String path = "no-stack-areas." + name;
        config.set(path + ".world", world);
        config.set(path + ".from", x1 + " " + z1);
        config.set(path + ".to", x2 + " " + z2);
        plugin.saveConfig();
        plugin.reloadPlugin();

        NoStackArea area = findArea(name);
        sender.sendMessage("Added the no-stack area " + (area == null ? name : area.describe())
                + ". Run /" + label + " unstack to split the stacks already inside it.");
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: area remove <name>");
            return;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (findArea(name) == null) {
            sender.sendMessage("There is no area called " + name + ". See the list with area list.");
            return;
        }
        plugin.getConfig().set("no-stack-areas." + name, null);
        plugin.saveConfig();
        plugin.reloadPlugin();
        sender.sendMessage("Removed the no-stack area " + name + ". Mobs there can be stacked again.");
    }

    private void list(CommandSender sender) {
        List<NoStackArea> areas = settings.get().noStackAreas();
        if (areas.isEmpty()) {
            sender.sendMessage("There are no no-stack areas.");
            return;
        }
        sender.sendMessage("No-stack areas (" + areas.size() + "):");
        for (NoStackArea area : areas) {
            sender.sendMessage("- " + area.describe());
        }
    }

    private void here(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this.");
            return;
        }
        NoStackArea area = settings.get().noStackAreaAt(player.getLocation());
        sender.sendMessage(area == null ? "You are not in a no-stack area."
                : "You are in the no-stack area " + area.describe() + ".");
    }

    private NoStackArea findArea(String name) {
        for (NoStackArea area : settings.get().noStackAreas()) {
            if (area.name().equals(name)) {
                return area;
            }
        }
        return null;
    }

    List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return startingWith(SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            return startingWith(settings.get().noStackAreas().stream().map(NoStackArea::name).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> startingWith(List<String> options, String typed) {
        String start = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(start)).toList();
    }
}
