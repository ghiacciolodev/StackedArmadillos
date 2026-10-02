package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Vanilla rolls Infested once each time a mob is hurt. A stack is a single
 * entity, so it gets one roll for all its armadillos. Here the stack gets the
 * missing rolls, one for every armadillo after the first, which vanilla
 * already rolled for.
 */
final class InfestedListener implements Listener {

    private final Supplier<Settings> settings;

    InfestedListener(Supplier<Settings> settings) {
        this.settings = settings;
    }

    // The damage event only fires for hits that get past the hurt cooldown,
    // which are the same hits vanilla rolls Infested for.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Armadillo armadillo)
                || !armadillo.hasPotionEffect(PotionEffectType.INFESTED)) {
            return;
        }
        Settings current = settings.get();
        if (!current.infestedEnabled() || !current.isActiveIn(armadillo.getWorld())) {
            return;
        }

        int stackAmount = WildStackerAPI.getEntityAmount(armadillo);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int roll = 1; roll < stackAmount; roll++) {
            if (random.nextFloat() <= current.infestedChance()) {
                int count = random.nextInt(current.minSilverfish(), current.maxSilverfish() + 1);
                for (int i = 0; i < count; i++) {
                    spawnSilverfish(armadillo, random);
                }
            }
        }
    }

    /** Same as vanilla: from the middle of the armadillo, thrown towards where it looks. */
    private static void spawnSilverfish(Armadillo armadillo, ThreadLocalRandom random) {
        Location look = armadillo.getLocation();
        Vector velocity = look.getDirection().multiply(0.3);
        velocity.setY(velocity.getY() * 1.5);
        velocity.rotateAroundY(random.nextDouble(-Math.PI / 2, Math.PI / 2));

        Location spawn = look.clone().add(0, armadillo.getHeight() / 2, 0);
        spawn.setYaw(random.nextFloat() * 360);
        spawn.setPitch(0);

        Silverfish silverfish = armadillo.getWorld().spawn(spawn, Silverfish.class,
                created -> created.setVelocity(velocity), SpawnReason.POTION_EFFECT);
        if (silverfish.isValid()) {
            float pitch = (random.nextFloat() - random.nextFloat()) * 0.2f + 1;
            silverfish.getWorld().playSound(silverfish, Sound.ENTITY_SILVERFISH_HURT, SoundCategory.HOSTILE, 1, pitch);
        }
    }
}
