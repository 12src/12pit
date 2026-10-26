package pit12.platform.mixin.world;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.EntityJoined;

@Mixin(WorldClient.class)
public abstract class OrnitheWorldClientMixin {
    @Inject(method = "spawnEntityInWorld", at = @At("RETURN"))
    private void pit12$entityJoined(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() || !ClientEvents.ENTITY_JOIN_LAST.listening())
            return;
        EntityJoined joined = new EntityJoined(entity, (WorldClient) (Object) this);
        ClientEvents.ENTITY_JOIN_LAST.publish(listener -> listener.accept(joined));
    }

    @Inject(method = "doPreChunk", at = @At("RETURN"))
    private void pit12$chunkLoaded(int x, int z, boolean load, CallbackInfo callback) {
        if (load && ClientEvents.CHUNK_LOAD.listening()) {
            Chunk chunk = ((WorldClient) (Object) this).getChunkFromChunkCoords(x, z);
            ClientEvents.CHUNK_LOAD.publish(listener -> listener.accept(chunk));
        }
    }
}
