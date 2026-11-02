/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.runtime.arrow;

import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

/** Callbacks run on the client thread. The bow is only borrowed during onArrowSpawn. */
public interface ArrowListener {
    /** Return true for removal and correction callbacks. lastSneakingNanos is zero when unknown. */
    boolean onArrowSpawn(EntityArrow arrow, EntityPlayer shooter, ItemStack bow, boolean sneaking,
            long lastSneakingNanos);

    /** local refers to the shooter. Negative disables matching. Zero matches within one client tick. */
    default long hitWindowNanos(boolean local) {
        return -1;
    }

    /** Candidates are nonempty. Only apply effects shared by all candidates. */
    default void onArrowHit(EntityPlayer target, List<ArrowHit> candidates, long hitAt) {}

    /** No later hit callback will use this arrow for the current listeners. */
    default void onArrowFinished(int entityId) {}

    default void onArrowRemoved(EntityArrow arrow, boolean serverRemoved) {}

    default void onArrowCorrected(EntityArrow arrow, boolean velocityChanged) {}

    /** Coordinates are the packet destination, including absolute position sync. */
    default void onShooterTeleport(EntityPlayer shooter, double x, double y, double z) {}

    default void onBlockChanged(BlockPos position) {}

    void onArrowsReset();
}
