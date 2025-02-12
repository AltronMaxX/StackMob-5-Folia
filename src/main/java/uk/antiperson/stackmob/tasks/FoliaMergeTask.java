package uk.antiperson.stackmob.tasks;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import uk.antiperson.stackmob.StackMob;
import uk.antiperson.stackmob.entity.StackEntity;
import uk.antiperson.stackmob.utils.Utilities;

import java.util.HashSet;
import java.util.Set;

// This is a very shitty way to port this task to be Folia compatible, but it works.
public class FoliaMergeTask implements Runnable {

    private final StackMob sm;

    public FoliaMergeTask(StackMob sm) {
        this.sm = sm;
    }

    @Override
    public void run() {
        boolean checkHasMoved = sm.getMainConfig().getConfig().isCheckHasMoved();
        double checkHasMovedDistance = sm.getMainConfig().getConfig().getCheckHasMovedDistance();
        for (StackEntity original : sm.getEntityManager().getStackEntities()) {
            original.getEntity().getScheduler().run(sm, scheduledTask -> {
                if (original.isWaiting()) {
                    original.incrementWait();
                    return;
                }
                if (!original.canStack()) {
                    if (!original.getEntity().isValid()) {
                        sm.getEntityManager().unregisterStackedEntity(original);
                    }
                    return;
                }
                if (checkHasMoved) {
                    if (original.getEntity().getWorld().equals(original.getLastLocation().getWorld())) {
                        if (original.getEntity().getLocation().distance(original.getLastLocation()) < checkHasMovedDistance) {
                            return;
                        }
                    }
                    original.setLastLocation(original.getEntity().getLocation());
                }
                boolean stackThresholdEnabled = original.getEntityConfig().getStackThresholdEnabled();
                Integer[] searchRadius = original.getEntityConfig().getStackRadius();
                Set<StackEntity> matches = new HashSet<>();
                for (Entity nearby : original.getEntity().getNearbyEntities(searchRadius[0], searchRadius[1], searchRadius[2])) {
                    if (!(nearby instanceof Mob)) {
                        continue;
                    }
                    StackEntity nearbyStack = sm.getEntityManager().getStackEntity((LivingEntity) nearby);
                    if (nearbyStack == null) {
                        continue;
                    }
                    if (!nearbyStack.canStack()) {
                        continue;
                    }
                    if (!original.match(nearbyStack)) {
                        continue;
                    }
                    if (!stackThresholdEnabled || (nearbyStack.getSize() > 1 || original.getSize() > 1)) {
                        final StackEntity removed = nearbyStack.merge(original, false);
                        if (removed != null) {
                            sm.getEntityManager().unregisterStackedEntity(removed);
                            if (original == removed) {
                                return;
                            }
                            break;
                        }
                        continue;
                    }
                    matches.add(nearbyStack);
                }
                if (!stackThresholdEnabled) {
                    return;
                }
                int threshold = original.getEntityConfig().getStackThreshold() - 1;
                int size = matches.size();
                if (size < threshold) {
                    return;
                }
                for (StackEntity match : matches) {
                    match.remove(false);
                    sm.getEntityManager().unregisterStackedEntity(match);
                }
                if (size + original.getSize() > original.getMaxSize()) {
                    final int toCompleteStack = (original.getMaxSize() - original.getSize());
                    original.incrementSize(toCompleteStack);
                    for (int stackSize : Utilities.split(size - toCompleteStack, original.getMaxSize())) {
                        StackEntity stackEntity = original.duplicate();
                        stackEntity.setSize(stackSize);
                    }
                    return;
                }
                original.incrementSize(size);
            }, () -> {});
        }
    }
}
