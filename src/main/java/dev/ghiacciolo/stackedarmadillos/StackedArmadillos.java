package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Armadillo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class StackedArmadillos extends JavaPlugin implements TabExecutor, Listener {

    // Read from async WildStacker events too, so always replaced as a whole.
    private volatile Settings settings;
    private BukkitTask crammingTask;
    private Unstacker unstacker;

    @Override
    public void onEnable() {
        // WildStacker is loaded before this plugin, but it can still fail to enable,
        // for example with an error in its config. Without it every stack lookup fails.
        if (!getServer().getPluginManager().isPluginEnabled("WildStacker")) {
            getLogger().severe("WildStacker is not enabled, so StackedArmadillos is turning itself off. "
                    + "Check the WildStacker errors earlier in the log, then restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        for (String warning : loadSettings()) {
            getLogger().warning(warning);
        }
        getLogger().info(settings.describe());

        var plugins = getServer().getPluginManager();
        plugins.registerEvents(this, this);
        plugins.registerEvents(new StackLimitListener(() -> settings), this);
        plugins.registerEvents(new InfestedListener(() -> settings), this);
        plugins.registerEvents(new ScuteListener(this, () -> settings), this);
        unstacker = new Unstacker(this);
        plugins.registerEvents(unstacker, this);
        startCrammingTask();

        PluginCommand command = getCommand("stackedarmadillos");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    // Turns this plugin off too if WildStacker is turned off while the server runs.
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("WildStacker")) {
            getLogger().severe("WildStacker was disabled, so StackedArmadillos is turning itself off.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Loads the config and returns warnings about it, if any. */
    private List<String> loadSettings() {
        reloadConfig();
        List<String> warnings = new ArrayList<>();
        settings = Settings.load(getConfig(), warnings);

        // A typo here makes the plugin inactive in that world without any error.
        for (String name : settings.worlds()) {
            if (getServer().getWorld(name) == null) {
                warnings.add("World '" + name + "' in the worlds list is not loaded. "
                        + "Check the name, it is case sensitive.");
            }
        }
        checkWildStacker(warnings);
        return warnings;
    }

    /** Points out WildStacker settings that work against this plugin. */
    private void checkWildStacker(List<String> warnings) {
        Plugin wildStacker = getServer().getPluginManager().getPlugin("WildStacker");
        if (wildStacker == null) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new File(wildStacker.getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException e) {
            // A tab instead of spaces is the usual cause. WildStacker can't read it either.
            warnings.add("The WildStacker config can't be read, so its settings can't be checked: "
                    + e.getMessage().lines().findFirst().orElse("") + ". Fix it, for example tabs used instead "
                    + "of spaces, then restart the server.");
            return;
        }

        // WildStacker only tries one nearby entity per merge. If that one is too
        // big and this plugin cancels the merge, it doesn't try another, so the
        // limit should be set in WildStacker too.
        int limit = entityValue(config, "entities.limits");
        if (settings.maxStackSize() > 0 && (limit <= 0 || limit > settings.maxStackSize())) {
            warnings.add("Set entities.limits.ARMADILLO to " + settings.maxStackSize()
                    + " in the WildStacker config, or armadillos may stop stacking once a stack is full.");
        }
        // With 0 WildStacker only tries to stack a mob when it spawns, and farm
        // armadillos are usually walked or pushed in, not spawned there.
        if (config.getInt("entities.stack-interval", 0) <= 0) {
            warnings.add("entities.stack-interval is 0 in the WildStacker config, so armadillos moved into a farm "
                    + "are never stacked. Set it to 100 there.");
        }
        int radius = entityValue(config, "entities.merge-radius");
        if (radius > 1) {
            warnings.add("entities.merge-radius for armadillos is " + radius + " in the WildStacker config. "
                    + "Set ARMADILLO to 1 there, so armadillos only stack with the ones in the same farm station.");
        }
        // WildStacker 2026.2 doesn't have this option and doesn't multiply armadillo
        // scutes. Later versions add it to their config, turned on.
        if (settings.scutesEnabled() && config.getBoolean("entities.multiply-armadillo-scutes", false)) {
            warnings.add("multiply-armadillo-scutes is true in the WildStacker config, so scutes are multiplied "
                    + "twice. Set it to false there, or set scutes.enabled to false here.");
        }
    }

    /** Reads the armadillo value of a WildStacker per-entity section, falling back to "all". */
    private static int entityValue(YamlConfiguration config, String path) {
        return config.getInt(path + ".ARMADILLO", config.getInt(path + ".all", 0));
    }

    private void startCrammingTask() {
        if (crammingTask != null) {
            crammingTask.cancel();
        }
        int interval = settings.crammingInterval();
        crammingTask = getServer().getScheduler().runTaskTimer(this, new CrammingTask(() -> settings), interval, interval);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /" + label + " <reload|status|unstack>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                List<String> warnings = loadSettings();
                startCrammingTask();
                sender.sendMessage("StackedArmadillos reloaded.");
                sender.sendMessage(settings.describe());
                for (String warning : warnings) {
                    sender.sendMessage("Warning: " + warning);
                }
            }
            case "status" -> {
                int entities = 0;
                int stacks = 0;
                int armadillos = 0;
                int biggest = 0;
                for (World world : getServer().getWorlds()) {
                    for (Armadillo armadillo : world.getEntitiesByClass(Armadillo.class)) {
                        int amount = WildStackerAPI.getEntityAmount(armadillo);
                        entities++;
                        armadillos += amount;
                        biggest = Math.max(biggest, amount);
                        if (amount > 1) {
                            stacks++;
                        }
                    }
                }
                sender.sendMessage("StackedArmadillos " + getPluginMeta().getVersion() + ": " + settings.describe());
                sender.sendMessage("Loaded armadillo entities: " + entities + ", of which stacks: " + stacks
                        + ", armadillos they stand for: " + armadillos + ", biggest stack: " + biggest + ".");
                List<String> warnings = new ArrayList<>();
                checkWildStacker(warnings);
                for (String warning : warnings) {
                    sender.sendMessage("Warning: " + warning);
                }
            }
            case "unstack" -> {
                boolean wasWatching = unstacker.isWatching();
                Map<String, Integer> split = unstacker.start(getServer().getWorlds());
                if (split.isEmpty()) {
                    sender.sendMessage("No stacks to split in the loaded chunks.");
                } else {
                    sender.sendMessage("Split the stacks of mobs WildStacker no longer stacks: " + split + ".");
                    getLogger().info(sender.getName() + " split stacks: " + split);
                }
                if (!wasWatching) {
                    sender.sendMessage("Chunks loaded from now on are checked too, until the next restart. "
                            + "Their splits are written in the log.");
                }
            }
            default -> sender.sendMessage("Usage: /" + label + " <reload|status|unstack>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "unstack").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
