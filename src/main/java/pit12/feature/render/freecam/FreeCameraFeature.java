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
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputHandler;
import pit12.runtime.input.InputTicks;
import pit12.runtime.session.ClientSession;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class FreeCameraFeature
        implements ClientLifecycle, ConfigChangeListener, InputHandler, InputTicks.Listener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final FreeCameraConfig config;
    private final ClientSession session;
    private final InputBlocker input;
    private final InputTicks inputTicks;
    private final FreeCameraBinding binding;
    private final Runnable sessionListener = this::onSessionChanged;
    private FreeCameraEntity camera;
    private EntityPlayerSP trackedPlayer;
    private boolean active;
    private boolean started;

    public FreeCameraFeature(Minecraft minecraft, ConfigCatalog configs, FreeCameraConfig config,
            ClientSession session, InputBlocker input, InputTicks inputTicks,
            FreeCameraBinding binding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.input = input;
        this.inputTicks = inputTicks;
        this.binding = binding;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        input.addHandler(this);
        inputTicks.addListener(this);
        session.addListener(sessionListener);
        binding.pit12$bindFreeCamera(this);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        configs.removeListener(this);
        input.removeHandler(this);
        inputTicks.removeListener(this);
        session.removeListener(sessionListener);
        deactivate();
        binding.pit12$bindFreeCamera(null);
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        for (Setting<?> setting : config.settings()) {
            if (changes.affects(config.id(), setting.id())) {
                updateActivation();
                return;
            }
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

    @Override
    public void onInputTick(InputTicks.Phase phase) {
        if (phase != InputTicks.Phase.START) {
            return;
        }
        tick();
    }

    public boolean isActive() {
        return active;
    }

    public boolean blocksInputs() {
        return active && config.blockPlayerInputs().get();
    }

    public boolean blocksMovement() {
        return active && config.blockPlayerMovement().get();
    }

    public boolean hidesHand() {
        return active && config.hideHand().get();
    }

    public FreeCameraEntity camera() {
        return camera;
    }

    public void rotateCamera(float yawChange, float pitchChange) {
        if (active && camera != null) {
            camera.rotate(yawChange, pitchChange);
        }
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled() && minecraft.thePlayer != null
                && minecraft.theWorld != null;
        if (enabled == active) {
            return;
        }
        if (enabled) {
            activate();
        } else {
            deactivate();
        }
    }

    private void activate() {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null) {
            return;
        }
        camera = new FreeCameraEntity(minecraft.theWorld);
        camera.placeAt(player.posX, player.posY + player.getEyeHeight(), player.posZ,
                player.rotationYaw, player.rotationPitch);
        minecraft.setRenderViewEntity(camera);
        trackedPlayer = player;
        active = true;
    }

    private void deactivate() {
        FreeCameraEntity current = camera;
        camera = null;
        trackedPlayer = null;
        active = false;
        // Without a player the game is leaving the world and resets the view entity itself.
        if (current != null && minecraft.thePlayer != null
                && minecraft.getRenderViewEntity() == current) {
            minecraft.setRenderViewEntity(minecraft.thePlayer);
        }
    }

    private void onSessionChanged() {
        // The world and the player instance change together, so rebuild the camera from scratch.
        deactivate();
        updateActivation();
    }

    private void tick() {
        if (!active) {
            return;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null) {
            deactivate();
            return;
        }
        if (camera == null || minecraft.getRenderViewEntity() != camera) {
            minecraft.setRenderViewEntity(camera);
        }
        if (player != trackedPlayer) {
            // Respawn replaces the player instance; keep the camera alive next to the new body.
            trackedPlayer = player;
            camera.placeAt(player.posX, player.posY + player.getEyeHeight(), player.posZ,
                    player.rotationYaw, player.rotationPitch);
        }
        // The camera entity never ticks, so refresh the interpolation anchor every tick ourselves.
        camera.syncTickPosition();
        if (!config.blockPlayerInputs().get()) {
            // Let the player share the camera's aim so that allowed clicks hit what you look at.
            player.prevRotationYaw = player.rotationYaw = camera.rotationYaw;
            player.prevRotationPitch = player.rotationPitch = camera.rotationPitch;
        }
        if (minecraft.currentScreen != null) {
            return;
        }
        moveCamera(player);
    }

    private void moveCamera(EntityPlayerSP player) {
        if (!config.blockPlayerMovement().get()) {
            // Movement is supposed to move the player, not the camera.
            return;
        }
        GameSettings settings = minecraft.gameSettings;
        double forward = axis(settings.keyBindForward, settings.keyBindBack);
        double strafe = axis(settings.keyBindLeft, settings.keyBindRight);
        double up = axis(settings.keyBindJump, settings.keyBindSneak);
        if (forward == 0.0 && strafe == 0.0 && up == 0.0) {
            return;
        }
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0) {
            forward /= length;
            strafe /= length;
        }
        double speed = config.speed().get();
        if (settings.keyBindSprint.isKeyDown()) {
            speed *= 2.0;
        }
        float yaw = camera.rotationYaw;
        double sin = Math.sin(yaw * Math.PI / 180.0);
        double cos = Math.cos(yaw * Math.PI / 180.0);
        double x = (strafe * cos - forward * sin) * speed;
        double z = (forward * cos + strafe * sin) * speed;
        double y = up * speed;
        double eyeY = player.posY + player.getEyeHeight();
        double targetX = camera.posX + x;
        double targetY = camera.posY + y;
        double targetZ = camera.posZ + z;
        double limit = config.maxDistance().get();
        double offsetX = targetX - player.posX;
        double offsetY = targetY - eyeY;
        double offsetZ = targetZ - player.posZ;
        double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        if (distanceSquared > limit * limit) {
            double scale = limit / Math.sqrt(distanceSquared);
            targetX = player.posX + offsetX * scale;
            targetY = eyeY + offsetY * scale;
            targetZ = player.posZ + offsetZ * scale;
        }
        camera.moveTo(targetX, targetY, targetZ);
    }

    private static double axis(KeyBinding positive, KeyBinding negative) {
        return (positive.isKeyDown() ? 1.0 : 0.0) - (negative.isKeyDown() ? 1.0 : 0.0);
    }
}
