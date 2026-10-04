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

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.session.ClientSession;
import pit12.shared.lifecycle.ClientLifecycle;

public final class PitContextTracker implements ClientLifecycle, PitContext {
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Z = 0;
    private final ClientSession session;
    private final Runnable sessionChanged = this::onSessionChanged;
    private WorldClient boundWorld;
    private PitSnapshot snapshot = new PitSnapshot(PitMap.UNKNOWN, 0L);
    private long revision;
    private boolean detectionComplete;
    private boolean started;

    public PitContextTracker(ClientSession session) {
        this.session = session;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
        session.addListener(sessionChanged);
        bindWorld(session.world());
    }

    @Override
    public void stop() {
        session.checkThread();
        if (started) {
            started = false;
            session.removeListener(sessionChanged);
            MinecraftForge.EVENT_BUS.unregister(this);
        }
        reset();
    }

    @Override
    public PitSnapshot current() {
        session.checkThread();
        return snapshot;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        session.checkThread();
        if (event.phase != Phase.START) {
            return;
        }
        WorldClient world = session.world();
        if (world != boundWorld) {
            bindWorld(world);
        }
        if (world == null || detectionComplete) {
            return;
        }
        if (!world.getChunkProvider().chunkExists(ORIGIN_X, ORIGIN_Z)) {
            return;
        }
        detect(world);
    }

    private void bindWorld(WorldClient world) {
        boundWorld = world;
        detectionComplete = false;
        replaceSnapshot(PitMap.UNKNOWN);
    }

    private void onSessionChanged() {
        bindWorld(session.world());
    }

    private void detect(WorldClient world) {
        boolean surfaceFound = false;
        for (PitMap candidate : PitMap.values()) {
            if (candidate == PitMap.UNKNOWN) {
                continue;
            }
            BlockPos position = new BlockPos(ORIGIN_X, candidate.spawnY(), ORIGIN_Z);
            while (position.getY() >= 0 && world.isAirBlock(position)) {
                position = position.down();
            }
            if (position.getY() < 0) {
                continue;
            }
            surfaceFound = true;
            IBlockState surface = world.getBlockState(position);
            if (!candidate.matchesSurface(surface)) {
                continue;
            }
            replaceSnapshot(candidate);
            detectionComplete = true;
            return;
        }
        if (!surfaceFound) {
            return;
        }
        replaceSnapshot(PitMap.UNKNOWN);
        detectionComplete = true;
    }

    private void reset() {
        boundWorld = null;
        detectionComplete = false;
        replaceSnapshot(PitMap.UNKNOWN);
    }

    private void replaceSnapshot(PitMap map) {
        snapshot = new PitSnapshot(map, ++revision);
    }
}
