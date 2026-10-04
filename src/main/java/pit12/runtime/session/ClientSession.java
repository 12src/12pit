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

import java.util.ArrayList;
import java.util.List;
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

/** One owner publishes connection and world changes after both identities have been updated. */
public final class ClientSession implements ClientLifecycle {
    private final Minecraft minecraft;
    private final ClientThread client;
    private final SessionState<NetHandlerPlayClient, WorldClient> state = new SessionState<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean started;
    private volatile long generation;

    public ClientSession(Minecraft minecraft, ClientThread client) {
        this.minecraft = minecraft;
        this.client = client;
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
        if (!listeners.contains(listener))
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
        generation++;
        MinecraftForge.EVENT_BUS.register(this);
        refresh();
    }

    @Override
    public void stop() {
        client.check();
        if (!started)
            return;
        started = false;
        generation++;
        MinecraftForge.EVENT_BUS.unregister(this);
        if (state.clear())
            Listeners.notify(listeners, Runnable::run);
        listeners.clear();
    }

    public void refresh() {
        client.check();
        if (started && state.update(minecraft.getNetHandler(), minecraft.theWorld)) {
            Listeners.notify(listeners, Runnable::run);
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase == Phase.START)
            refresh();
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        long eventGeneration = generation;
        client.execute(() -> {
            if (!started || generation != eventGeneration)
                return;
            refresh();
            NetHandlerPlayClient current = state.connection();
            if (current != null && current.getNetworkManager() == event.manager
                    && state.disconnect(current)) {
                Listeners.notify(listeners, Runnable::run);
            }
        });
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.world instanceof WorldClient))
            return;
        long eventGeneration = generation;
        client.execute(() -> {
            if (started && generation == eventGeneration
                    && state.unload((WorldClient) event.world)) {
                Listeners.notify(listeners, Runnable::run);
            }
        });
    }
}
