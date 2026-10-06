package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.objects.StackedEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Reads WildStacker stacks without failing on entities WildStacker can't stack. */
final class Stacks {

    private Stacks() {
    }

    /**
     * The WildStacker stack of an entity, or null for entities WildStacker
     * can't stack: armor stands, mannequins, players, and mobs other plugins
     * keep for themselves, like NPCs. WildStacker throws for those.
     */
    static StackedEntity of(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return null;
        }
        try {
            return WildStackerAPI.getStackedEntity(entity);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** How many mobs an entity stands for, 1 if WildStacker can't stack it. */
    static int amount(LivingEntity entity) {
        StackedEntity stack = of(entity);
        return stack == null ? 1 : stack.getStackAmount();
    }
}
