package pit12.platform.pylon;

import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import pit12.runtime.input.InputDevice;
import pit12.shared.input.Keybind;
import pl.tomgirl.pylon.window.KeyboardSdl;
import pl.tomgirl.pylon.window.MouseSdl;

public final class PylonInputDevice implements InputDevice {
    private static boolean blocked;
    private final KeyboardSdl keyboard = KeyboardSdl.instance();
    private final MouseSdl mouse = MouseSdl.instance();
    private final Deque<Runnable> presses = new ArrayDeque<>();
    private KeyPressHandler keyPressHandler;
    private Runnable mousePressHandler;
    private int modifiers;

    public static boolean blocksInput() {
        return blocked;
    }

    @Override
    public void block(KeyPressHandler handler, Runnable mousePressHandler) {
        keyPressHandler = handler;
        this.mousePressHandler = mousePressHandler;
        modifiers = 0;
        pollAndDrain(false);
        if (Keyboard.isCreated()) {
            for (int key : new int[] {Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT,
                    Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL, Keyboard.KEY_LMENU,
                    Keyboard.KEY_RMENU, Keyboard.KEY_LMETA, Keyboard.KEY_RMETA}) {
                if (Keyboard.isKeyDown(key))
                    modifiers |= Keybind.modifierMask(key);
            }
        }
        blocked = true;
    }

    @Override
    public void dispatchEvents() {
        pollAndDrain(true);
        // Callbacks can release the input lock, so finish polling first.
        Runnable press;
        while ((press = presses.pollFirst()) != null)
            press.run();
    }

    @Override
    public void unblock() {
        try {
            pollAndDrain(false);
        } finally {
            blocked = false;
            keyPressHandler = null;
            mousePressHandler = null;
            presses.clear();
        }
    }

    private void pollAndDrain(boolean deliver) {
        if (Keyboard.isCreated()) {
            Keyboard.poll();
            KeyboardSdl.Event event;
            // Read the backend queue directly so other mods don't receive blocked key events.
            while ((event = keyboard.nextKeyboardEvent()) != null) {
                int key = event.keycode;
                int modifier = Keybind.modifierMask(key);
                boolean pressed = event.state != 0;
                if (pressed)
                    modifiers |= modifier;
                else
                    modifiers &= ~modifier;
                if (deliver && pressed && !event.repeat) {
                    KeyPressHandler handler = keyPressHandler;
                    int heldModifiers = modifiers;
                    presses.addLast(() -> handler.key(key, heldModifiers));
                }
            }
        }
        if (Mouse.isCreated()) {
            Mouse.poll();
            MouseSdl.Event event;
            while ((event = mouse.nextMouseEvent()) != null) {
                if (deliver && !Mouse.isGrabbed() && Mouse.isInsideWindow()
                        && event.button >= 0 && event.state)
                    presses.addLast(mousePressHandler);
            }
            Mouse.getDX();
            Mouse.getDY();
            Mouse.getDWheel();
        }
    }
}
