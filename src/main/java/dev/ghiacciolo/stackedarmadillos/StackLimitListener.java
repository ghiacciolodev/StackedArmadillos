package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.events.EntityStackEvent;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Armadillo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Stops any merge inside a no-stack area. For armadillos it also stops merges
 * over max-stack-size, or between different blocks. A merge removes one of the
 * two entities, so without the block check an armadillo could end up in the
 * stack of the farm station next to it.
 */
final class StackLimitListener implements Listener {

    private final Supplier<Settings> settings;

    StackLimitListener(Supplier<Settings> settings) {
        this.settings = settings;
    }

    // WildStacker may call this off the main thread, so only read values here.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStack(EntityStackEvent event) {
        Settings current = settings.get();
        // Any mob, whitelisted or not, if either side of the merge is in an area.
        if (current.noStackAreaAt(event.getEntity().getLivingEntity().getLocation()) != null
                || current.noStackAreaAt(event.getTarget().getLivingEntity().getLocation()) != null) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getEntity().getLivingEntity() instanceof Armadillo armadillo)
                || !current.isActiveIn(armadillo.getWorld())) {
            return;
        }
        int merged = event.getEntity().getStackAmount() + event.getTarget().getStackAmount();
        if (current.maxStackSize() > 0 && merged > current.maxStackSize()) {
            event.setCancelled(true);
        } else if (current.sameBlockOnly()
                && !sameBlock(armadillo.getLocation(), event.getTarget().getLivingEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
