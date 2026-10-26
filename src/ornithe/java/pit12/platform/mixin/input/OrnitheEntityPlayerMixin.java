package pit12.platform.mixin.input;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Attack;

@Mixin(EntityPlayer.class)
public abstract class OrnitheEntityPlayerMixin {
    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"))
    private void pit12$attacking(Entity target, CallbackInfo callback) {
        if (!ClientEvents.ATTACK.listening() && !ClientEvents.ATTACK_LAST.listening())
            return;
        Attack attack = new Attack((EntityPlayer) (Object) this, target);
        ClientEvents.ATTACK.publish(listener -> listener.accept(attack));
        ClientEvents.ATTACK_LAST.publish(listener -> listener.accept(attack));
    }
}
