package pit12.platform.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Overlay;

@Mixin(GuiIngame.class)
public abstract class OrnitheGuiIngameMixin {
    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void pit12$renderOverlay(float partialTicks, CallbackInfo callback) {
        if (!ClientEvents.OVERLAY.listening())
            return;
        Overlay overlay = new Overlay(new ScaledResolution(Minecraft.getMinecraft()), partialTicks);
        ClientEvents.OVERLAY.publish(listener -> listener.accept(overlay));
    }
}
