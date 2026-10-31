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
package pit12.feature.render.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputHandler;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class FreeCameraFeature
        implements ClientLifecycle, ConfigChangeListener, InputHandler {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final FreeCameraConfig config;
    private final InputBlocker input;
    private final FreeCameraBinding binding;
    private FreeCameraEntity camera;
    private EntityPlayerSP trackedPlayer;
    private Entity previousView;
    private boolean previousChunkCulling;
    private boolean started;

    public FreeCameraFeature(Minecraft minecraft, ConfigCatalog configs, FreeCameraConfig config,
            InputBlocker input, FreeCameraBinding binding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.input = input;
        this.binding = binding;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        input.addHandler(this);
        MinecraftForge.EVENT_BUS.register(this);
        binding.pit12$bindFreeCamera(this);
        updateCamera();
    }

    @Override
    public void stop() {
        started = false;
        configs.removeListener(this);
        input.removeHandler(this);
        MinecraftForge.EVENT_BUS.unregister(this);
        deactivate();
        binding.pit12$bindFreeCamera(null);
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (started && changes.affects("freecam", "enabled")) {
            updateCamera();
        }
    }

    @Override
    public boolean consumesKey(int key) {
        return Keybind.matches(config.toggleKey().get(), key);
    }

    @Override
    public boolean key(int key, boolean pressed, boolean repeat) {
        if (!pressed || repeat || minecraft.currentScreen != null
                || !Keybind.matches(config.toggleKey().get(), key)) {
            return false;
        }
        config.setEnabled(!config.enabled());
        return true;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        updateCamera();
        if (camera == null || minecraft.isGamePaused()) {
            return;
        }
        camera.onUpdate();
        if (minecraft.currentScreen != null || !minecraft.inGameHasFocus || input.locked()) {
            return;
        }
        GameSettings settings = minecraft.gameSettings;
        double forward = axis(settings.keyBindForward, settings.keyBindBack);
        double strafe = axis(settings.keyBindLeft, settings.keyBindRight);
        double up = axis(settings.keyBindJump, settings.keyBindSneak);
        if (forward == 0.0 && strafe == 0.0 && up == 0.0) {
            return;
        }
        double speed = config.speed().get();
        if (settings.keyBindSprint.isKeyDown()) {
            speed *= 2.0;
        }
        double lengthSquared = forward * forward + strafe * strafe + up * up;
        if (lengthSquared > 1.0) {
            speed /= Math.sqrt(lengthSquared);
        }
        double yaw = Math.toRadians(camera.rotationYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        camera.moveEntity((strafe * cos - forward * sin) * speed, up * speed,
                (forward * cos + strafe * sin) * speed);
    }

    public boolean isActive() {
        return camera != null;
    }

    public EntityPlayerSP camera() {
        return camera;
    }

    private void updateCamera() {
        EntityPlayerSP player = minecraft.thePlayer;
        if (!started || !config.enabled() || player == null || minecraft.theWorld == null
                || player.isDead) {
            deactivate();
            return;
        }
        if (camera != null && (player != trackedPlayer || camera.worldObj != minecraft.theWorld)) {
            deactivate();
        }
        if (camera == null) {
            trackedPlayer = player;
            previousView = minecraft.getRenderViewEntity();
            previousChunkCulling = minecraft.renderChunksMany;
            camera = new FreeCameraEntity(minecraft, player);
            minecraft.renderChunksMany = false;
            input.clearMovement(player);
            if (minecraft.playerController != null) {
                minecraft.playerController.resetBlockRemoving();
                if (player.isUsingItem()) {
                    minecraft.playerController.onStoppedUsingItem(player);
                }
            }
        }
        if (minecraft.getRenderViewEntity() != camera) {
            minecraft.setRenderViewEntity(camera);
        }
    }

    private void deactivate() {
        if (camera == null) {
            return;
        }
        FreeCameraEntity current = camera;
        Entity view = previousView;
        if (view == trackedPlayer || view == null || view.worldObj != minecraft.theWorld) {
            view = minecraft.thePlayer;
        }
        camera = null;
        trackedPlayer = null;
        previousView = null;
        minecraft.renderChunksMany = previousChunkCulling;
        if (minecraft.getRenderViewEntity() == current) {
            minecraft.setRenderViewEntity(view);
        }
        minecraft.entityRenderer.getMouseOver(1.0F);
    }

    private static double axis(KeyBinding positive, KeyBinding negative) {
        return (positive.isKeyDown() ? 1.0 : 0.0) - (negative.isKeyDown() ? 1.0 : 0.0);
    }
}
