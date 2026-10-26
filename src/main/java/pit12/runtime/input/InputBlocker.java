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
package pit12.runtime.input;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.ClientEvents;
import pit12.shared.lifecycle.ClientLifecycle;

public final class InputBlocker implements ClientLifecycle {
    private final Runnable onTickListener = this::onTick;

    private final Minecraft minecraft;
    private final ClientThread client;
    private final InputBlockBinding binding;
    private final InputDevice device;
    private final Set<Object> owners = new HashSet<>();
    private final List<InputHandler> handlers = new ArrayList<>();
    private Object actionOwner;
    private boolean cursorReleased;
    private boolean started;

    public InputBlocker(Minecraft minecraft, ClientThread client, InputBlockBinding binding,
            InputDevice device) {
        this.minecraft = minecraft;
        this.client = client;
        this.binding = binding;
        this.device = device;
    }

    @Override
    public void start() {
        client.check();
        if (started)
            return;
        started = true;
        binding.pit12$bindInputBlocker(this);
    }

    @Override
    public void stop() {
        client.check();
        if (locked()) {
            owners.clear();
            release(false);
        }
        handlers.clear();
        binding.pit12$bindInputBlocker(null);
        started = false;
    }

    public void addHandler(InputHandler handler) {
        client.check();
        if (!handlers.contains(handler))
            handlers.add(handler);
    }

    public void removeHandler(InputHandler handler) {
        client.check();
        handlers.remove(handler);
    }

    public void lock(Object owner) {
        client.check();
        boolean alreadyLocked = locked();
        owners.add(owner);
        if (alreadyLocked)
            return;
        device.block(this::blockedKey, this::focusOnClick);
        ClientEvents.INPUT_TICK.add(onTickListener);
        KeyBinding.unPressAllKeys();
        if (minecraft.playerController != null) {
            minecraft.playerController.resetBlockRemoving();
            if (minecraft.thePlayer != null && minecraft.thePlayer.isUsingItem())
                minecraft.playerController.onStoppedUsingItem(minecraft.thePlayer);
        }
        EntityPlayerSP player = minecraft.thePlayer;
        if (player != null) {
            player.movementInput.moveForward = 0;
            player.movementInput.moveStrafe = 0;
            player.movementInput.jump = false;
            player.movementInput.sneak = false;
            player.moveForward = 0;
            player.moveStrafing = 0;
            player.setJumping(false);
            player.setSprinting(false);
        }
        minecraft.mouseHelper.deltaX = 0;
        minecraft.mouseHelper.deltaY = 0;
        if (minecraft.entityRenderer != null)
            ((MouseInputBinding) minecraft.entityRenderer).pit12$clearMouseInput();
    }

    public void unlock(Object owner, boolean resume) {
        client.check();
        if (owners.remove(owner) && !locked())
            release(resume);
    }

    public boolean locked() {
        return !owners.isEmpty();
    }

    public boolean blockedFor(Object owner) {
        return locked() && (owners.size() != 1 || !owners.contains(owner));
    }

    public boolean blocksActions() {
        return locked() && (actionOwner == null || blockedFor(actionOwner));
    }

    public void releaseCursor() {
        client.check();
        cursorReleased = true;
        minecraft.setIngameNotInFocus();
    }

    public boolean blocksFocus() {
        return locked() && cursorReleased;
    }

    private void focusOnClick() {
        if (!cursorReleased || !Display.isActive())
            return;
        cursorReleased = false;
        // setIngameFocus closes the current menu.
        if (minecraft.currentScreen == null)
            minecraft.setIngameFocus();
    }

    public void runAction(Object owner, Runnable action) {
        client.check();
        // Let the lock owner use vanilla interaction hooks without restoring physical input.
        Object previous = actionOwner;
        actionOwner = owner;
        try {
            action.run();
        } finally {
            actionOwner = previous;
        }
    }

    public boolean key(int key, boolean pressed, boolean repeat) {
        if (locked())
            return true;
        for (InputHandler handler : handlers) {
            if (handler.key(key, pressed, repeat))
                return true;
        }
        return false;
    }

    private void onTick() {
        device.dispatchEvents();
    }

    private void blockedKey(int key, int modifiers) {
        for (Object owner : owners) {
            if (owner instanceof InputHandler && ((InputHandler) owner).blockedKey(key, modifiers))
                return;
        }
    }

    private void release(boolean resume) {
        ClientEvents.INPUT_TICK.remove(onTickListener);
        cursorReleased = false;
        device.unblock();
        minecraft.mouseHelper.deltaX = 0;
        minecraft.mouseHelper.deltaY = 0;
        KeyBinding.unPressAllKeys();
        if (!resume || !minecraft.inGameHasFocus || !Display.isActive())
            return;
        // Held keys can come back, but don't replay presses made while blocked.
        for (KeyBinding keyBinding : minecraft.gameSettings.keyBindings) {
            int key = keyBinding.getKeyCode();
            boolean consumed = false;
            for (InputHandler handler : handlers) {
                if (handler.consumesKey(key)) {
                    consumed = true;
                    break;
                }
            }
            if (!consumed)
                KeyBinding.setKeyBindState(key, held(key));
        }
    }

    public static boolean held(int key) {
        if (key > 0 && key < Keyboard.KEYBOARD_SIZE)
            return Keyboard.isCreated() && Keyboard.isKeyDown(key);
        int button = key + 100;
        return key < 0 && Mouse.isCreated() && button >= 0 && button < Mouse.getButtonCount()
                && Mouse.isButtonDown(button);
    }
}
