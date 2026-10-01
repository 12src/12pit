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
package pit12.feature.swap;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import pit12.feature.swap.api.SwapBindings;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.session.ClientSession;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;

public final class SwapFeature implements ClientLifecycle, ConfigChangeListener, SwapHooks {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final SwapConfig config;
    private final ClientSession session;
    private final BindingBook bindings;
    private final SwapController controller;
    private final SwapOverlay overlay;
    private final SwapHooksBinding hookBinding;
    private final Runnable sessionListener = this::worldChanged;
    private boolean started;
    private boolean listening;
    private boolean rightClickHeld;
    private boolean mouseGrabbed;
    private boolean inputLocked;

    public SwapFeature(Minecraft minecraft, ClientThread client, ConfigCatalog configs,
            SwapConfig config, ClientSession session, CommandRegistry commands, Path path,
            SwapHooksBinding hookBinding) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.hookBinding = hookBinding;
        bindings = new BindingBook(client, path, this::report);
        controller = new SwapController(minecraft, session, config, bindings, this::report,
                this::lockInput, this::releaseInput);
        overlay = new SwapOverlay(minecraft, bindings, config);
        commands.register(new SwapCommand(minecraft, bindings, config), true);
    }

    public SwapBindings bindings() {
        return bindings;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        configs.addListener(this);
        session.addListener(sessionListener);
        bindings.start();
        updateListening();
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started)
            return;
        started = false;
        configs.removeListener(this);
        session.removeListener(sessionListener);
        updateListening();
        bindings.stop();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("swap", "enabled"))
            updateListening();
    }

    private void updateListening() {
        boolean enabled = started && config.enabled();
        if (enabled == listening)
            return;
        listening = enabled;
        if (enabled) {
            MinecraftForge.EVENT_BUS.register(this);
            hookBinding.pit12$bindSwapHooks(this);
        } else {
            hookBinding.pit12$bindSwapHooks(null);
            MinecraftForge.EVENT_BUS.unregister(this);
            controller.cancel();
            updateMouseGrab();
            overlay.clear();
            rightClickHeld = false;
        }
    }

    private void worldChanged() {
        controller.abandon();
        updateMouseGrab();
        overlay.clear();
        rightClickHeld = false;
    }

    private void lockInput() {
        inputLocked = true;
        KeyBinding.unPressAllKeys();
    }

    private void releaseInput(boolean resume) {
        if (Keyboard.isCreated())
            while (Keyboard.next()) {
            }
        if (Mouse.isCreated()) {
            while (Mouse.next()) {
            }
            Mouse.getDX();
            Mouse.getDY();
        }
        minecraft.mouseHelper.deltaX = 0;
        minecraft.mouseHelper.deltaY = 0;
        KeyBinding.unPressAllKeys();
        inputLocked = false;
        if (!resume || !minecraft.inGameHasFocus || !Display.isActive())
            return;
        // Restore held states without replaying presses buffered while the inventory was open.
        for (KeyBinding binding : minecraft.gameSettings.keyBindings) {
            int key = binding.getKeyCode();
            if (listening && (key == config.unequipKey.get() || bindings.hasKey(key)))
                continue;
            KeyBinding.setKeyBindState(key, physicallyHeld(key));
        }
    }

    private static boolean physicallyHeld(int key) {
        if (key > 0 && key < Keyboard.KEYBOARD_SIZE)
            return Keyboard.isCreated() && Keyboard.isKeyDown(key);
        int button = key + 100;
        return key < 0 && Mouse.isCreated() && button >= 0 && button < Mouse.getButtonCount()
                && Mouse.isButtonDown(button);
    }

    @Override
    public boolean inputLocked() {
        return inputLocked
                && (minecraft.currentScreen == null || controller.owns(minecraft.currentScreen));
    }

    @Override
    public boolean key(int key, boolean pressed, boolean repeat) {
        if (inputLocked())
            return true;
        if (!pressed || key <= 0 || key >= Keyboard.KEYBOARD_SIZE || !listening
                || !controller.acceptsInput())
            return false;
        boolean unequip = key == controller.unequipKey();
        if (!unequip && !bindings.hasKey(key))
            return false;
        if (!repeat) {
            if (unequip)
                controller.enqueueUnequip();
            else
                controller.enqueueKey(key);
        }
        return true;
    }

    private boolean rightClick() {
        if (inputLocked())
            return true;
        if (rightClickHeld)
            return true;
        if (!listening || !controller.rightClickEnabled() || minecraft.currentScreen != null
                || !controller.acceptsInput())
            return false;
        ItemStack held = minecraft.thePlayer.getHeldItem();
        int target = SwapBinding.armorTarget(held);
        if (target == 0 || minecraft.thePlayer.inventory.armorInventory[target - 1] == null
                || PitEnchantmentReader.contains(held, PitEnchantment.Trash_Panda))
            return false;
        MovingObjectPosition hit = minecraft.objectMouseOver;
        // Server-side entity interactions may succeed even when the client reports no result.
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY)
            return false;
        controller.enqueueArmor(SwapBinding.create(1, held, 0));
        rightClickHeld = true;
        return true;
    }

    @Override
    public void drawBinding(ItemStack stack, int x, int y) {
        overlay.draw(stack, x, y);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (!physicallyHeld(minecraft.gameSettings.keyBindUseItem.getKeyCode()))
            rightClickHeld = false;
        controller.tick();
        updateMouseGrab();
        overlay.refresh();
    }

    private void updateMouseGrab() {
        boolean hidden = controller.hidden() && controller.owns(minecraft.currentScreen);
        if (hidden) {
            if (!Mouse.isGrabbed())
                Mouse.setGrabbed(true);
        } else if (mouseGrabbed && minecraft.currentScreen != null && Mouse.isGrabbed()) {
            Mouse.setGrabbed(false);
        }
        mouseGrabbed = hidden;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (controller.owns(event.gui))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (controller.owns(event.gui))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGameMouse(MouseEvent event) {
        if (inputLocked()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.entityPlayer == minecraft.thePlayer
                && event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR && rightClick()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (controller.owns(event.gui) && controller.hidden())
            event.setCanceled(true);
    }

    private void report(String message) {
        if (minecraft.thePlayer != null)
            SwapCommand.reply(minecraft.thePlayer, message);
    }
}
