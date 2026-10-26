package pit12.platform.mixin.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.ScreenDraw;

@Mixin(EntityRenderer.class)
public abstract class OrnitheEntityRendererMixin {
    @Inject(method = "renderWorldPass", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z"))
    private void pit12$renderWorld(int pass, float partialTicks, long finishTime,
            CallbackInfo callback) {
        // The hand pass clears depth, so world overlays run just before it.
        ClientEvents.WORLD_RENDER.publish(listener -> listener.render(partialTicks));
        ClientEvents.WORLD_RENDER_LAST.publish(listener -> listener.render(partialTicks));
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V"))
    private void pit12$drawScreen(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (ClientEvents.GUI_DRAW.listening()) {
            ScreenDraw draw = new ScreenDraw(screen);
            ClientEvents.GUI_DRAW.publish(listener -> listener.accept(draw));
            if (draw.canceled)
                return;
        }
        screen.drawScreen(mouseX, mouseY, partialTicks);
    }
}
