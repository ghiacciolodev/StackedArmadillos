package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.objects.StackedEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Splits the stacks of mobs that WildStacker no longer stacks, for example
 * after the whitelist was changed. WildStacker keeps those stacks as they are,
 * so a mob type taken off the whitelist stays stacked forever.
 */
final class Unstacker implements Listener {

    // Fewer than the vanilla cramming limit of 24, so the split mobs never cram.
    private static final int PER_BLOCK = 8;
    private static final int SPREAD_RADIUS = 2;

    private final Logger logger;
    private boolean watching;

    Unstacker(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    /** Splits the stacks in all loaded chunks and keeps splitting in chunks loaded later. */
    Map<String, Integer> start(Iterable<World> worlds) {
        watching = true;
        Map<String, Integer> split = new TreeMap<>();
        for (World world : worlds) {
            for (LivingEntity entity : world.getLivingEntities()) {
                splitIfNeeded(entity, split);
            }
        }
        return split;
    }

    boolean isWatching() {
        return watching;
    }

    // Stacks in chunks nobody has visited yet are only seen when the chunk loads.
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!watching) {
            return;
        }
        Map<String, Integer> split = new TreeMap<>();
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                splitIfNeeded(living, split);
            }
        }
        if (!split.isEmpty()) {
            logger.info("Split stacks in a loaded chunk: " + split);
        }
    }

    private void splitIfNeeded(LivingEntity entity, Map<String, Integer> split) {
        if (!entity.isValid()) {
            return;
        }
        StackedEntity stack = Stacks.of(entity);
        if (stack == null) {
            return;
        }
        int amount = stack.getStackAmount();
        // isCached is false for mobs WildStacker would no longer stack.
        if (amount <= 1 || stack.isCached()) {
            return;
        }

        List<Location> spots = spots(entity.getLocation(), amount);
        for (int i = 1; i < amount; i++) {
            // spawnDuplicate copies the mob data, like its variant, color and age.
            LivingEntity copy = stack.spawnDuplicate(1).getLivingEntity();
            copy.teleport(spots.get(i));
        }
        stack.setStackAmount(1, true);
        clearStackName(entity, amount);
        split.merge(entity.getType().getKey().getKey(), amount, Integer::sum);
    }

    // WildStacker doesn't update the name of mobs it no longer stacks, so the
    // "x30 Cow" name would stay. A name given with a name tag is kept.
    private static void clearStackName(LivingEntity entity, int amount) {
        Component name = entity.customName();
        if (name != null && PlainTextComponentSerializer.plainText().serialize(name).startsWith("x" + amount + " ")) {
            entity.customName(null);
            entity.setCustomNameVisible(false);
        }
    }

    /**
     * Picks a spot for every mob, at most PER_BLOCK in each block, in the free
     * blocks around the stack at the same height. The first spot is the stack
     * itself. If there isn't enough room, the rest stay where the stack is.
     */
    private static List<Location> spots(Location origin, int amount) {
        List<Block> blocks = new ArrayList<>();
        Block center = origin.getBlock();
        blocks.add(center);
        for (int dx = -SPREAD_RADIUS; dx <= SPREAD_RADIUS; dx++) {
            for (int dz = -SPREAD_RADIUS; dz <= SPREAD_RADIUS; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                if ((dx != 0 || dz != 0) && fits(block, center)) {
                    blocks.add(block);
                }
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Location> spots = new ArrayList<>(amount);
        spots.add(origin);
        for (int i = 1; i < amount; i++) {
            int index = i / PER_BLOCK;
            if (index >= blocks.size()) {
                spots.add(origin.clone());
                continue;
            }
            Block block = blocks.get(index);
            Location spot = index == 0 ? origin.clone()
                    : block.getLocation().add(0.3 + random.nextDouble(0.4), 0, 0.3 + random.nextDouble(0.4));
            spot.setYaw(random.nextFloat() * 360);
            spots.add(spot);
        }
        return spots;
    }

    /** A block a mob can stand in: open, with an open block above, and ground below or water like the stack. */
    private static boolean fits(Block block, Block center) {
        if (!block.isPassable() || !block.getRelative(0, 1, 0).isPassable()) {
            return false;
        }
        if (center.isLiquid()) {
            return block.isLiquid();
        }
        return !block.isLiquid() && block.getRelative(0, -1, 0).getType().isSolid();
    }
}
