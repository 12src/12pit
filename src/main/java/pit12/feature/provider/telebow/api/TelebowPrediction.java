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
package pit12.feature.provider.telebow.api;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/** Use on the client thread. Matching a shot or teleport does not require cooldown tracking. */
public interface TelebowPrediction {
    long hitWindowNanos(boolean local);

    boolean matchesTeleport(Vec3 landing, Vec3 destination, long timeDifference, boolean sameTick,
            boolean local);

    /** A zero audience releases this owner's cooldown demand. */
    void setCooldownDemand(Object owner, int audience);

    boolean blocksArrow(UUID shooterId, int arrowId);

    /** Returns the cooldown deadline in nanoseconds, or zero when no cooldown is tracked. */
    long cooldownUntil(UUID playerId);

    long cooldownRevision();

    void visitCooldowns(int audience, CooldownVisitor visitor);

    interface CooldownVisitor {
        void accept(EntityPlayer player, long until, int group);
    }
}
