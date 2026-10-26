package pit12.platform.mixin.client;

import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;

@Mixin(TextureMap.class)
public abstract class OrnitheTextureMapMixin {
    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void pit12$texturesReloaded(CallbackInfo callback) {
        ClientEvents.TEXTURES_RELOADED.publish(Runnable::run);
    }
}
