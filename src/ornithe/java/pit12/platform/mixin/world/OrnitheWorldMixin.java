package pit12.platform.mixin.world;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Sound;

@Mixin(World.class)
public abstract class OrnitheWorldMixin {
    @Inject(method = "playSoundAtEntity", at = @At("HEAD"))
    private void pit12$soundPlayed(Entity entity, String name, float volume, float pitch,
            CallbackInfo callback) {
        if (!ClientEvents.SOUND.listening())
            return;
        Sound sound = new Sound(entity, name);
        ClientEvents.SOUND.publish(listener -> listener.accept(sound));
    }
}
