package dev.ghiacciolo.stackedarmadillos;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.objects.StackedEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Armadillo;

/**
 * Vanilla entity cramming counts entities, and a stack of 24 armadillos is one
 * entity, so a 25th armadillo in the same block would never be crammed. This
 * task counts the armadillos in each block including the ones in stacks, keeps
 * the biggest stacks up to the limit and kills the rest.
 */
final class CrammingTask implements Runnable {

    // Biggest stacks first, then the oldest, so the newest armadillo is the one that dies.
    private static final Comparator<Member> KEEP_ORDER = Comparator
            .comparingInt(Member::amount).reversed()
            .thenComparing(Comparator.comparingInt((Member member) -> member.armadillo().getTicksLived()).reversed());

    private final Supplier<Settings> settings;

    CrammingTask(Supplier<Settings> settings) {
        this.settings = settings;
    }

    private record Member(Armadillo armadillo, StackedEntity stack, int amount) {
    }

    @Override
    public void run() {
        Settings current = settings.get();
        if (!current.crammingEnabled()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (!current.isActiveIn(world)) {
                continue;
            }
            int limit = current.crammingLimit() < 0
                    ? world.getGameRuleValue(GameRules.MAX_ENTITY_CRAMMING)
                    : current.crammingLimit();
            if (limit > 0) {
                checkWorld(world, limit, current.crammingDamage());
            }
        }
    }

    private static void checkWorld(World world, int limit, double damage) {
        Map<Block, List<Armadillo>> byBlock = new HashMap<>();
        for (Armadillo armadillo : world.getEntitiesByClass(Armadillo.class)) {
            // Vanilla doesn't count passengers either.
            if (armadillo.isValid() && !armadillo.isInsideVehicle()) {
                byBlock.computeIfAbsent(armadillo.getLocation().getBlock(), k -> new ArrayList<>(1)).add(armadillo);
            }
        }

        for (List<Armadillo> block : byBlock.values()) {
            List<Member> members = new ArrayList<>(block.size());
            int total = 0;
            boolean hasStack = false;
            for (Armadillo armadillo : block) {
                StackedEntity stack = WildStackerAPI.getStackedEntity(armadillo);
                int amount = stack.getStackAmount();
                members.add(new Member(armadillo, stack, amount));
                total += amount;
                hasStack |= amount > 1;
            }
            // Without stacks the vanilla cramming already sees every armadillo.
            if (hasStack && total > limit) {
                cram(members, limit, damage);
            }
        }
    }

    private static void cram(List<Member> members, int limit, double damage) {
        members.sort(KEEP_ORDER);
        int room = limit;
        for (Member member : members) {
            if (member.amount() <= room) {
                room -= member.amount();
                continue;
            }
            int excess = member.amount() - room;
            room = 0;
            if (member.amount() > 1) {
                // Armadillos inside a stack can't be hurt one by one, so they are
                // taken out of it. The last one is hurt like a single armadillo.
                member.stack().decreaseStackAmount(Math.min(excess, member.amount() - 1), true);
            }
            if (excess == member.amount()) {
                member.armadillo().damage(damage, DamageSource.builder(DamageType.CRAMMING).build());
            }
        }
    }
}
