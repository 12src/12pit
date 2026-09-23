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
package pit12.feature.pit;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import pit12.feature.Feature;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitMap;
import pit12.runtime.pit.PitSnapshot;

public final class PitContextFeature implements Feature, PitContext {
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Z = 0;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private WorldClient boundWorld;
    private PitSnapshot snapshot = PitSnapshot.initial();
    private long revision;
    private boolean detectionComplete;
    private boolean started;

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            reset();
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        reset();
    }

    @Override
    public PitSnapshot current() {
        return snapshot;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        WorldClient world = minecraft.theWorld;
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

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote && event.world == boundWorld) {
            reset();
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        reset();
    }

    private void bindWorld(WorldClient world) {
        boundWorld = world;
        detectionComplete = false;
        replaceSnapshot(PitMap.UNKNOWN);
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
