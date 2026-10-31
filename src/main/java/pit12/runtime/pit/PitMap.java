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
    UNKNOWN(0, 0, null, 0, null),
    GENESIS(86, 26, Blocks.air, 0, PitMid.GENESIS),
    CASTLE(95, 20, Blocks.stained_glass, 11, PitMid.STANDARD),
    CORALS(114, 23, Blocks.gold_block, 0, PitMid.STANDARD),
    SEASONS(114, 23, Blocks.double_stone_slab, 9, PitMid.STANDARD),
    ELEMENTS(114, 23, Blocks.double_wooden_slab, 5, PitMid.STANDARD);

    private final int spawnY;
    private final int spawnRadius;
    private final Block surfaceBlock;
    private final int surfaceMetadata;
    private final PitMid mid;

    PitMap(int spawnY, int spawnRadius, Block surfaceBlock, int surfaceMetadata, PitMid mid) {
        this.spawnY = spawnY;
        this.spawnRadius = spawnRadius;
        this.surfaceBlock = surfaceBlock;
        this.surfaceMetadata = surfaceMetadata;
        this.mid = mid;
    }

    public boolean matchesSurface(IBlockState state) {
        Block block = state.getBlock();
        if (this == SEASONS && block == Blocks.sandstone) {
            return block.getMetaFromState(state) == 2;
        }
        if (this == ELEMENTS && block == Blocks.planks) {
            return block.getMetaFromState(state) == 5;
        }
        return this != UNKNOWN && block == surfaceBlock
                && block.getMetaFromState(state) == surfaceMetadata;
    }

    public int spawnY() {
        return spawnY;
    }

    /** Returns null for UNKNOWN. */
    public PitMid mid() {
        return mid;
    }

    public boolean isNearMid(double x, double z) {
        return this != UNKNOWN && withinSpawnRadius(x, z);
    }

    SpawnState spawnStateAt(double x, double y, double z) {
        if (this == UNKNOWN) {
            return SpawnState.UNKNOWN;
        }
        return y + 1 >= spawnY && withinSpawnRadius(x, z) ? SpawnState.IN_SPAWN
                : SpawnState.OUTSIDE_SPAWN;
    }

    private boolean withinSpawnRadius(double x, double z) {
        return Math.abs(x) <= spawnRadius && Math.abs(z) <= spawnRadius;
    }
}
