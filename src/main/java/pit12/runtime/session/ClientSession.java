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
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.World;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.Listeners;
import pit12.shared.lifecycle.ClientLifecycle;

/** Both connection and world are updated before listeners run. */
public final class ClientSession implements ClientLifecycle {
    private final Consumer<NetworkManager> onDisconnectListener = this::onDisconnect;
    private final Consumer<World> onWorldUnloadListener = this::onWorldUnload;

    private final Minecraft minecraft;
    private final ClientThread client;
    private final ClientSessionBinding binding;
    private final SessionState<NetHandlerPlayClient, WorldClient> state = new SessionState<>();
    private final List<Runnable> listeners = new ArrayList<>();
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
        // noinspection NonAtomicOperationOnVolatileField
        generation++;
        binding.bindWorldChangeListener(this::refresh);
        ClientEvents.DISCONNECT.add(onDisconnectListener);
        ClientEvents.WORLD_UNLOAD.add(onWorldUnloadListener);
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
        ClientEvents.DISCONNECT.remove(onDisconnectListener);
        ClientEvents.WORLD_UNLOAD.remove(onWorldUnloadListener);
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

    private void onDisconnect(NetworkManager manager) {
        long eventGeneration = generation;
        client.execute(() -> {
            if (!started || generation != eventGeneration)
                return;
            refresh();
            NetHandlerPlayClient current = state.connection();
            if (current != null && current.getNetworkManager() == manager
                    && state.disconnect(current)) {
                Listeners.notify(listeners, Runnable::run);
            }
        });
    }

    private void onWorldUnload(World world) {
        if (!(world instanceof WorldClient))
            return;
        long eventGeneration = generation;
        client.execute(() -> {
            if (started && generation == eventGeneration
                    && state.unload((WorldClient) world)) {
                Listeners.notify(listeners, Runnable::run);
            }
        });
    }
}
