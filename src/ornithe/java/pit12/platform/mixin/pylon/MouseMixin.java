package pit12.platform.mixin.pylon;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.platform.pylon.PylonInputDevice;

@Mixin(value = Mouse.class, remap = false)
public abstract class MouseMixin {
    @Inject(method = {"isButtonDown", "next"}, at = @At("HEAD"), cancellable = true)
    private static void pit12$blockMouse(CallbackInfoReturnable<Boolean> callback) {
        if (PylonInputDevice.blocksInput())
            callback.setReturnValue(false);
    }

    @Inject(method = {"getDX", "getDY", "getDWheel"}, at = @At("RETURN"), cancellable = true)
    private static void pit12$blockMouseMovement(CallbackInfoReturnable<Integer> callback) {
        if (PylonInputDevice.blocksInput())
            callback.setReturnValue(0);
    }

    @Dynamic("Added by Pylon")
    @Inject(method = "getDWheelPrecise", at = @At("RETURN"), cancellable = true)
    private static void pit12$blockPreciseWheel(CallbackInfoReturnable<Double> callback) {
        if (PylonInputDevice.blocksInput())
            callback.setReturnValue(0.0);
    }
}
