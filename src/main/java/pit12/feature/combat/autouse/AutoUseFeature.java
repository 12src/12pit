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
package pit12.feature.combat.autouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputTicks;
import pit12.runtime.input.InputTicks.Phase;
import pit12.runtime.languages.Languages;
import pit12.runtime.session.ClientSession;
import pit12.shared.input.MinecraftActions;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoUseFeature
        implements ClientLifecycle, ConfigChangeListener, InputTicks.Listener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final AutoUseConfig config;
    private final ClientSession session;
    private final InputBlocker input;
    private final InputTicks inputTicks;
    private final AutoUseController controller;
    private final AutoUseInventory inventory;
    private final Runnable sessionListener = this::onSessionChanged;
    private EntityPlayerSP player;
    private long sessionRevision;
    private boolean started;
    private boolean active;
    private boolean listening;

    public AutoUseFeature(Minecraft minecraft, ConfigCatalog configs, AutoUseConfig config,
            ClientSession session, InputBlocker input, InputTicks inputTicks,
            MinecraftActions actions, Languages language) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.input = input;
        this.inputTicks = inputTicks;
        controller = new AutoUseController(config, actions);
        inventory = new AutoUseInventory(minecraft, config, controller, input, language);
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        configs.addListener(this);
        session.addListener(sessionListener);
        updateActivation();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started)
            return;
        started = false;
        configs.removeListener(this);
        session.removeListener(sessionListener);
        updateActivation();
        inventory.updatePlayer(null, session.revision());
    }

    private void onSessionChanged() {
        inventory.updatePlayer(
                session.world() == null ? null : inventoryPlayer(minecraft.thePlayer),
                session.revision());
        updateListening();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects(config.id(), "enabled"))
            updateActivation();
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (active && !enabled) {
            if (player != null && player == minecraft.thePlayer && player.isEntityAlive()
                    && sessionRevision == session.revision() && !input.locked()) {
                controller.stop(player.inventory, player.isUsingItem());
            }
            controller.reset();
        } else if (!active && enabled) {
            inventory.resumeTransfers();
        }
        active = enabled;
        updateListening();
    }

    private void updateListening() {
        boolean enabled = started && (active || inventory.hasWork());
        if (listening == enabled)
            return;
        listening = enabled;
        if (enabled) {
            inputTicks.addListener(this);
            MinecraftForge.EVENT_BUS.register(this);
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            inputTicks.removeListener(this);
            player = null;
        }
    }

    @Override
    public void onInputTick(Phase phase) {
        if (!listening)
            return;
        if (phase == Phase.START) {
            EntityPlayerSP current = minecraft.thePlayer;
            long revision = session.revision();
            inventory.updatePlayer(inventoryPlayer(current), revision);
            inventory.tick();
            if (player != current || sessionRevision != revision
                    || (current != null && !current.isEntityAlive())) {
                controller.reset();
                player = current;
                sessionRevision = revision;
            }
            controller.tick();
            updateListening();
            return;
        }
        if (phase == Phase.GUI_INPUT || phase == Phase.GUI_UPDATE) {
            inventory.advance(phase, active);
            return;
        }
        if (phase != Phase.BEFORE_ACTIONS && phase != Phase.AFTER_INPUT)
            return;
        if (phase == Phase.AFTER_INPUT) {
            inventory.advance(phase, active);
            updateListening();
        }
        if (!active)
            return;
        if (player == null || player != minecraft.thePlayer || minecraft.theWorld == null
                || minecraft.currentScreen != null || !minecraft.inGameHasFocus || input.locked()
                || !player.isEntityAlive() || player.isSpectator()) {
            controller.cancelClick();
            return;
        }
        if (phase == Phase.BEFORE_ACTIONS) {
            // Vanilla has changed slots but hasn't handled clicks yet.
            controller.beforeActions(player.inventory, player.isUsingItem());
            return;
        }
        if (player.isUsingItem()) {
            controller.cancelClick();
            return;
        }
        GameSettings settings = minecraft.gameSettings;
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)
                || InputBlocker.held(settings.keyBindAttack.getKeyCode())
                || InputBlocker.held(settings.keyBindUseItem.getKeyCode()))
            return;
        controller.tryUse(player.inventory, player.getHealth(),
                inventory.available() ? inventory : null);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (inventory.hides(event.gui))
            event.setCanceled(true);
    }

    private static EntityPlayerSP inventoryPlayer(EntityPlayerSP candidate) {
        return candidate != null && candidate.isEntityAlive() && !candidate.isSpectator()
                ? candidate
                : null;
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onMouseInput(MouseEvent event) {
        if (minecraft.currentScreen != null)
            return;
        if (event.dwheel != 0)
            controller.manualSlotChange();
        if (event.buttonstate)
            onPress(event.button - 100, event.button == 0 || event.button == 1);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (minecraft.currentScreen == null && Keyboard.getEventKeyState())
            onPress(Keyboard.getEventKey(), false);
    }

    private void onPress(int key, boolean mouseClick) {
        GameSettings settings = minecraft.gameSettings;
        for (KeyBinding slot : settings.keyBindsHotbar) {
            if (slot.getKeyCode() == key) {
                controller.manualSlotChange();
                return;
            }
        }
        if (mouseClick || key == settings.keyBindAttack.getKeyCode()
                || key == settings.keyBindUseItem.getKeyCode())
            controller.manualClick();
    }
}
