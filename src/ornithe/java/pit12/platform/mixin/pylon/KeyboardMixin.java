package pit12.platform.mixin.pylon;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.platform.pylon.PylonInputDevice;

// Pylon loads these classes through Knot, where Mixin can reach them.
@Mixin(value = Keyboard.class, remap = false)
public abstract class KeyboardMixin {
    @Inject(method = {"isKeyDown", "next"}, at = @At("HEAD"), cancellable = true)
    private static void pit12$blockKeyboard(CallbackInfoReturnable<Boolean> callback) {
        if (PylonInputDevice.blocksInput())
            callback.setReturnValue(false);
    }

    @Inject(method = "getNumKeyboardEvents", at = @At("HEAD"), cancellable = true)
    private static void pit12$hidePendingKeys(CallbackInfoReturnable<Integer> callback) {
        if (PylonInputDevice.blocksInput())
            callback.setReturnValue(0);
    }
}
