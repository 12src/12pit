package pit12.platform.mixin.feature.swap;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.swap.SwapHooks;
import pit12.feature.swap.SwapHooksBinding;

@Mixin(Minecraft.class)
public abstract class OrnitheTickMixin {
    @Inject(method = "runTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;rightClickDelayTimer:I", ordinal = 0))
    private void pit12$startSwapTick(CallbackInfo callback) {
        SwapHooks hooks = ((SwapHooksBinding) this).pit12$swapHooks();
        if (hooks != null)
            hooks.inventoryTick(0);
    }
}
