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
package pit12.feature.provider.timertracking.api;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;

/** Use on the client thread. A zero selection releases this owner's demand. */
public interface TimerTracking {
    int PIN = 1;
    int VENOM = 2;
    int JUMP_BOOST = 4;

    void setDemand(Object owner, int selection);

    long revision();

    void visit(int selection, Visitor visitor);

    /** Returns colored countdowns with a leading space, or an empty string. */
    String countdowns(UUID playerId, int types);

    interface Visitor {
        void accept(TimerKind kind, EntityPlayer player, long until, int group);
    }
}
