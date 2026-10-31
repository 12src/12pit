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
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class InputBlocker implements ClientLifecycle {
    private final Minecraft minecraft;
    private final ClientThread client;
    private final InputBlockBinding binding;
    private final Supplier<KeyBinding> preservedKey;
    private final Set<Object> owners = new HashSet<>();
    private final List<InputHandler> handlers = new ArrayList<>();
    private Object actionOwner;
    private EntityPlayerSP sprintPlayer;
    private int sprintForwardKey;
    private int modifiers;
    private volatile boolean locked;
    private boolean cursorReleased;
    private boolean started;

    public InputBlocker(Minecraft minecraft, ClientThread client, InputBlockBinding binding,
            Supplier<KeyBinding> preservedKey) {
        this.minecraft = minecraft;
        this.client = client;
        this.binding = binding;
        this.preservedKey = preservedKey;
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
            locked = false;
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
        locked = true;
        if (alreadyLocked)
            return;
        EntityPlayerSP player = minecraft.thePlayer;
        sprintPlayer = player != null && ((SprintInputBinding) player).pit12$pauseDoubleTapSprint()
                ? player
                : null;
        sprintForwardKey = minecraft.gameSettings.keyBindForward.getKeyCode();
        if (!held(sprintForwardKey))
            sprintPlayer = null;
        modifiers = 0;
        for (int key : new int[] {Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT, Keyboard.KEY_LCONTROL,
                Keyboard.KEY_RCONTROL, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU, Keyboard.KEY_LMETA,
                Keyboard.KEY_RMETA}) {
            if (held(key))
                modifiers |= Keybind.modifierMask(key);
        }
        KeyBinding.unPressAllKeys();
        if (minecraft.playerController != null) {
            minecraft.playerController.resetBlockRemoving();
            if (minecraft.thePlayer != null && minecraft.thePlayer.isUsingItem())
                minecraft.playerController.onStoppedUsingItem(minecraft.thePlayer);
        }
        if (player != null)
            clearMovement(player);
    }

    public void unlock(Object owner, boolean resume) {
        client.check();
        if (owners.remove(owner) && owners.isEmpty()) {
            locked = false;
            release(resume);
        }
    }

    public boolean locked() {
        return locked;
    }

    public boolean blockedFor(Object owner) {
        return locked() && (owners.size() != 1 || !owners.contains(owner));
    }

    public boolean blocksActions() {
        return locked() && (!minecraft.isCallingFromMinecraftThread() || actionOwner == null
                || blockedFor(actionOwner));
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
        Object previous = actionOwner;
        actionOwner = owner;
        try {
            action.run();
        } finally {
            actionOwner = previous;
        }
    }

    public boolean key(int key, boolean pressed, boolean repeat) {
        if (locked()) {
            blockedKey(key, pressed, repeat);
            return true;
        }
        for (InputHandler handler : handlers) {
            if (handler.key(key, pressed, repeat))
                return true;
        }
        return false;
    }

    public void drainEvents() {
        if (Keyboard.isCreated()) {
            while (Keyboard.next())
                blockedKey(Keyboard.getEventKey(), Keyboard.getEventKeyState(),
                        Keyboard.isRepeatEvent());
        }
        if (Mouse.isCreated()) {
            while (Mouse.next()) {
                int button = Mouse.getEventButton();
                boolean pressed = Mouse.getEventButtonState();
                if (!pressed && sprintForwardKey == button - 100)
                    sprintPlayer = null;
                if (locked() && button >= 0 && pressed && !Mouse.isGrabbed()
                        && Mouse.isInsideWindow())
                    focusOnClick();
            }
        }
        if (!Display.isActive() || !held(sprintForwardKey))
            sprintPlayer = null;
    }

    public void clearMovement(EntityPlayerSP player) {
        player.movementInput.moveForward = 0;
        player.movementInput.moveStrafe = 0;
        player.movementInput.jump = false;
        player.movementInput.sneak = false;
        player.moveForward = 0;
        player.moveStrafing = 0;
        player.setJumping(false);
    }

    private void blockedKey(int key, boolean pressed, boolean repeat) {
        if (!pressed && key == sprintForwardKey)
            sprintPlayer = null;
        int modifier = Keybind.modifierMask(key);
        if (pressed)
            modifiers |= modifier;
        else
            modifiers &= ~modifier;
        if (!pressed || repeat)
            return;
        for (Object owner : owners) {
            if (owner instanceof InputHandler && ((InputHandler) owner).blockedKey(key, modifiers))
                return;
        }
    }

    private void release(boolean resume) {
        cursorReleased = false;
        if (Keyboard.isCreated())
            Keyboard.poll();
        if (Mouse.isCreated())
            Mouse.poll();
        drainEvents();
        EntityPlayerSP player = sprintPlayer;
        sprintPlayer = null;
        if (Mouse.isCreated())
            Mouse.getDWheel();
        KeyBinding pressedKey = preservedKey.get();
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
                KeyBinding.setKeyBindState(key, held(key) || keyBinding == pressedKey);
        }
        if (player != null && player == minecraft.thePlayer
                && sprintForwardKey == minecraft.gameSettings.keyBindForward.getKeyCode()
                && minecraft.gameSettings.keyBindForward.isKeyDown())
            ((SprintInputBinding) player).pit12$resumeDoubleTapSprint();
    }

    public static boolean held(int key) {
        if (key > 0 && key < Keyboard.KEYBOARD_SIZE)
            return Keyboard.isCreated() && Keyboard.isKeyDown(key);
        int button = key + 100;
        return key < 0 && Mouse.isCreated() && button >= 0 && button < Mouse.getButtonCount()
                && Mouse.isButtonDown(button);
    }
}
