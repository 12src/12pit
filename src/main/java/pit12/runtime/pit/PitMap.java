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
package pit12.runtime.pit;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

public enum PitMap {
    UNKNOWN(0, 0, null, 0),
    GENESIS(86, 26, Blocks.wool, 7),
    CASTLE(95, 20, Blocks.double_wooden_slab, 5),
    CORALS(114, 23, Blocks.stained_hardened_clay, 12),
    SEASONS(114, 23, Blocks.stone, 3),
    ELEMENTS(114, 23, Blocks.dirt, 0);

    private final int spawnY;
    private final int spawnRadius;
    private final Block surfaceBlock;
    private final int surfaceMetadata;

    PitMap(int spawnY, int spawnRadius, Block surfaceBlock, int surfaceMetadata) {
        this.spawnY = spawnY;
        this.spawnRadius = spawnRadius;
        this.surfaceBlock = surfaceBlock;
        this.surfaceMetadata = surfaceMetadata;
    }

    public boolean matchesSurface(IBlockState state) {
        return this != UNKNOWN && state != null && state.getBlock() == surfaceBlock
                && surfaceBlock.getMetaFromState(state) == surfaceMetadata;
    }

    public int spawnY() {
        return spawnY;
    }

    /** Returns UNKNOWN when the surface block does not identify one of the known maps. */
    public static PitMap fromSurface(IBlockState surface) {
        for (PitMap map : values()) {
            if (map.matchesSurface(surface)) {
                return map;
            }
        }
        return UNKNOWN;
    }

    SpawnState spawnStateAt(double x, double y, double z) {
        if (this == UNKNOWN) {
            return SpawnState.UNKNOWN;
        }
        return y > spawnY && Math.abs(x) <= spawnRadius && Math.abs(z) <= spawnRadius
                ? SpawnState.IN_SPAWN
                : SpawnState.OUTSIDE_SPAWN;
    }
}
