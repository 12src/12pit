package pit12.platform.mixin.client;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.NameRender;

@Mixin(RendererLivingEntity.class)
public abstract class OrnitheRendererLivingEntityMixin {
    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void pit12$renderedEntity(EntityLivingBase entity, double x, double y, double z,
            float yaw, float partialTicks, CallbackInfo callback) {
        if (!ClientEvents.NAME_RENDER.listening())
            return;
        NameRender render = new NameRender(entity, (RendererLivingEntity<?>) (Object) this,
                x, y, z, entity.isSneaking() ? 32 : 64);
        ClientEvents.NAME_RENDER.publish(listener -> listener.accept(render));
    }
}
