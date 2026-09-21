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
package pit12.feature.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.feature.Feature;

public final class TooltipFeature implements Feature {
    private final TooltipConfig config;
    private HeldItemTooltipBinding binding;
    private boolean listening;
    private boolean started;

    public TooltipFeature(TooltipConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        listening = true;
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        stopListening();
        if (binding != null) {
            binding.pit12$bindTooltipConfig(null);
            binding = null;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        Object ingameGui = Minecraft.getMinecraft().ingameGUI;
        if (!(ingameGui instanceof HeldItemTooltipBinding)) {
            return;
        }
        binding = (HeldItemTooltipBinding) ingameGui;
        binding.pit12$bindTooltipConfig(config);
        stopListening();
    }

    private void stopListening() {
        if (listening) {
            MinecraftForge.EVENT_BUS.unregister(this);
            listening = false;
        }
    }
}
