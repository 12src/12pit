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
package pit12.feature.sprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoSprintFeature implements ClientLifecycle {
    private final AutoSprintConfig config;
    private boolean started;

    public AutoSprintFeature(AutoSprintConfig config) {
        this.config = config;
    }

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
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        Object player = Minecraft.getMinecraft().thePlayer;
        if (player instanceof AutoSprintBinding) {
            ((AutoSprintBinding) player).pit12$bindSprintConfig(null);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        // Respawns and dimension changes replace the player entity.
        Object player = Minecraft.getMinecraft().thePlayer;
        if (player instanceof AutoSprintBinding) {
            ((AutoSprintBinding) player).pit12$bindSprintConfig(config);
        }
    }
}
