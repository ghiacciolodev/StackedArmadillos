package satest;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.events.EntityStackEvent;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

// Test helper: counts Infested spawns per block and reads stack sizes that commands can't show.
public final class SaTest extends JavaPlugin implements Listener {

    private final Map<String, Integer> infested = new TreeMap<>();
    private final Map<String, Integer> armadilloDeaths = new TreeMap<>();
    private boolean cancelSilverfish = true;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    // Counts every silverfish spawned by Infested by the block it spawned in, then
    // cancels it so thousands of silverfish don't pile up during the tests.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Silverfish
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.POTION_EFFECT) {
            Location l = event.getLocation();
            infested.merge(l.getBlockX() + "," + l.getBlockZ(), 1, Integer::sum);
            if (cancelSilverfish) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Armadillo) {
            armadilloDeaths.merge(event.getDamageSource().getDamageType().getKey().getKey(), 1, Integer::sum);
        }
    }

    // Armadillos tagged "nostack" never stack, to have vanilla armadillos next to stacked ones.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onStack(EntityStackEvent event) {
        if (event.getEntity().getLivingEntity().getScoreboardTags().contains("nostack")
                || event.getTarget().getLivingEntity().getScoreboardTags().contains("nostack")) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        World world = getServer().getWorlds().get(0);
        switch (args[0]) {
            case "stats" -> sender.sendMessage("infested=" + infested + " armadilloDeaths=" + armadilloDeaths);
            case "clear" -> {
                // Removes armadillos without killing them: killing a stack only kills one of it.
                int removed = 0;
                for (Armadillo armadillo : world.getEntitiesByClass(Armadillo.class)) {
                    armadillo.remove();
                    removed++;
                }
                sender.sendMessage("removed " + removed);
            }
            case "resetstats" -> {
                infested.clear();
                armadilloDeaths.clear();
                sender.sendMessage("stats reset");
            }
            case "cancelsilverfish" -> {
                cancelSilverfish = Boolean.parseBoolean(args[1]);
                sender.sendMessage("cancelSilverfish=" + cancelSilverfish);
            }
            case "setstack" -> {
                // setstack <tag> <amount>
                int done = 0;
                for (Armadillo armadillo : world.getEntitiesByClass(Armadillo.class)) {
                    if (armadillo.getScoreboardTags().contains(args[1])) {
                        WildStackerAPI.getStackedEntity(armadillo).setStackAmount(Integer.parseInt(args[2]), true);
                        done++;
                    }
                }
                sender.sendMessage("setstack " + done);
            }
            case "block" -> {
                // block <x> <y> <z>: armadillo entities in that block, their stack size and health
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                int entities = 0;
                int total = 0;
                StringBuilder list = new StringBuilder();
                for (Armadillo armadillo : world.getEntitiesByClass(Armadillo.class)) {
                    Location l = armadillo.getLocation();
                    if (armadillo.isValid() && l.getBlockX() == x && l.getBlockY() == y && l.getBlockZ() == z) {
                        int amount = WildStackerAPI.getEntityAmount(armadillo);
                        entities++;
                        total += amount;
                        list.append(amount).append('/').append(String.format("%.1f", armadillo.getHealth()))
                            .append('/').append(armadillo.getState()).append(' ');
                    }
                }
                sender.sendMessage("entities=" + entities + " total=" + total + " list=" + list.toString().trim());
            }
            case "items" -> {
                // items <x> <y> <z> <radius> <material>: total amount of that item around a point
                Location center = new Location(world, Double.parseDouble(args[1]), Double.parseDouble(args[2]),
                        Double.parseDouble(args[3]));
                double r = Double.parseDouble(args[4]);
                Material type = Material.matchMaterial(args[5]);
                int items = 0;
                int amount = 0;
                for (Entity entity : world.getNearbyEntities(center, r, r, r)) {
                    if (entity instanceof Item item && item.getItemStack().getType() == type) {
                        items++;
                        amount += WildStackerAPI.getItemAmount(item);
                    }
                }
                sender.sendMessage("items=" + items + " amount=" + amount);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
