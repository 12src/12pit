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
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.shared.lifecycle.ClientLifecycle;

public final class TooltipFeature implements ClientLifecycle, ConfigChangeListener {
    private final ConfigCatalog configs;
    private final TooltipConfig config;
    private HeldItemTooltipBinding binding;
    private boolean listening;
    private boolean started;

    public TooltipFeature(ConfigCatalog configs, TooltipConfig config) {
        this.configs = configs;
        this.config = config;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        if (config.enabled()) {
            bindOrListen();
        }
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        stopListening();
        unbind();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (!started || !changes.affects("tooltip", "enabled")) {
            return;
        }
        if (config.enabled()) {
            bindOrListen();
        } else {
            stopListening();
            unbind();
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        bindOrListen();
    }

    private void bindOrListen() {
        Object ingameGui = Minecraft.getMinecraft().ingameGUI;
        if (!(ingameGui instanceof HeldItemTooltipBinding)) {
            if (!listening) {
                MinecraftForge.EVENT_BUS.register(this);
                listening = true;
            }
            return;
        }
        binding = (HeldItemTooltipBinding) ingameGui;
        binding.pit12$bindTooltipConfig(config);
        stopListening();
    }

    private void unbind() {
        if (binding != null) {
            binding.pit12$bindTooltipConfig(null);
            binding = null;
        }
    }

    private void stopListening() {
        if (listening) {
            MinecraftForge.EVENT_BUS.unregister(this);
            listening = false;
        }
    }
}
