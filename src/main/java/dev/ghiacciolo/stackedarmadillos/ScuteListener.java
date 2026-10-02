package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * Multiplies the scute an armadillo stack sheds over time by the stack size,
 * because every armadillo in it would have shed one. Brushing is left alone:
 * in vanilla a brush takes one scute from one armadillo.
 */
final class ScuteListener implements Listener {

    private final Plugin plugin;
    private final Supplier<Settings> settings;

    // Armadillos brushed in the current tick. Their scute comes from the brush.
    private final Set<UUID> brushed = new HashSet<>();

    ScuteListener(Plugin plugin, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBrush(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Armadillo armadillo
                && event.getPlayer().getInventory().getItem(event.getHand()).getType() == Material.BRUSH) {
            markBrushed(armadillo);
        }
    }

    // A dispenser with a brush brushes the armadillos in the block in front of it.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispenserBrush(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.BRUSH
                || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        Block front = event.getBlock().getRelative(directional.getFacing());
        for (Entity entity : front.getWorld().getNearbyEntities(BoundingBox.of(front),
                entity -> entity instanceof Armadillo)) {
            markBrushed(entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Armadillo armadillo)
                || event.getItemDrop().getItemStack().getType() != Material.ARMADILLO_SCUTE
                || brushed.contains(armadillo.getUniqueId())) {
            return;
        }
        Settings current = settings.get();
        if (!current.scutesEnabled() || !current.isActiveIn(armadillo.getWorld())) {
            return;
        }
        int stackAmount = WildStackerAPI.getEntityAmount(armadillo);
        if (stackAmount > 1) {
            multiply(event.getItemDrop(), stackAmount);
        }
    }

    /** Multiplies the dropped item, spilling into more items past the max stack size. */
    private static void multiply(Item drop, int times) {
        ItemStack item = drop.getItemStack();
        int max = item.getMaxStackSize();
        int total = item.getAmount() * times;

        item.setAmount(Math.min(total, max));
        drop.setItemStack(item);
        for (int left = total - item.getAmount(); left > 0; left -= max) {
            ItemStack extra = item.clone();
            extra.setAmount(Math.min(left, max));
            drop.getWorld().dropItem(drop.getLocation(), extra, spawned -> spawned.setVelocity(drop.getVelocity()));
        }
    }

    private void markBrushed(Entity armadillo) {
        if (brushed.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, brushed::clear);
        }
        brushed.add(armadillo.getUniqueId());
    }
}
