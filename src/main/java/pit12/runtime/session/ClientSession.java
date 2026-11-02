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
package pit12.runtime.session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

/** Session changes are published after one quiet client tick. */
public final class ClientSession implements ClientLifecycle {
    private final Minecraft minecraft;
    private final ClientThread client;
    private final ClientSessionBinding binding;
    private final SessionState<NetHandlerPlayClient, WorldClient> state = new SessionState<>();
    private final Listeners<Runnable> listeners = new Listeners<>();
    private long lastTickRevision;
    private long notifiedRevision;
    private boolean started;
    private volatile long generation;

    public ClientSession(Minecraft minecraft, ClientThread client, ClientSessionBinding binding) {
        this.minecraft = minecraft;
        this.client = client;
        this.binding = binding;
    }

    public NetHandlerPlayClient connection() {
        client.check();
        return state.connection();
    }

    public WorldClient world() {
        client.check();
        return state.world();
    }

    public long revision() {
        client.check();
        return state.revision();
    }

    public void checkThread() {
        client.check();
    }

    public void addListener(Runnable listener) {
        client.check();
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        client.check();
        listeners.remove(listener);
    }

    @Override
    public void start() {
        client.check();
        if (started)
            return;
        started = true;
        // noinspection NonAtomicOperationOnVolatileField
        generation++;
        lastTickRevision = state.revision();
        notifiedRevision = lastTickRevision;
        binding.bindWorldChangeListener(this::refresh);
        MinecraftForge.EVENT_BUS.register(this);
        refresh();
    }

    @Override
    public void stop() {
        client.check();
        if (!started)
            return;
        started = false;
        // noinspection NonAtomicOperationOnVolatileField
        generation++;
        binding.bindWorldChangeListener(null);
        MinecraftForge.EVENT_BUS.unregister(this);
        state.clear();
        if (notifiedRevision != state.revision()) {
            notifiedRevision = state.revision();
            listeners.notify(Runnable::run);
        }
        listeners.clear();
    }

    public void refresh() {
        client.check();
        if (started) {
            state.update(minecraft.getNetHandler(), minecraft.theWorld);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        client.check();
        if (!started || event.phase != Phase.END) {
            return;
        }
        long revision = state.revision();
        if (lastTickRevision != revision) {
            lastTickRevision = revision;
            return;
        }
        if (notifiedRevision != revision) {
            notifiedRevision = revision;
            listeners.notify(Runnable::run);
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        long eventGeneration = generation;
        client.execute(() -> {
            if (!started || generation != eventGeneration)
                return;
            refresh();
            NetHandlerPlayClient current = state.connection();
            if (current != null && current.getNetworkManager() == event.manager) {
                state.disconnect(current);
            }
        });
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.world instanceof WorldClient))
            return;
        long eventGeneration = generation;
        client.execute(() -> {
            if (started && generation == eventGeneration) {
                state.unload((WorldClient) event.world);
            }
        });
    }
}
