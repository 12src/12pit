package pit12.platform.mixin.input;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Interaction;

@Mixin(PlayerControllerMP.class)
public abstract class OrnithePlayerControllerMPMixin {
    @Inject(method = "onPlayerRightClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;syncCurrentPlayItem()V",
            shift = At.Shift.AFTER))
    private void pit12$interactBlock(EntityPlayerSP player, WorldClient world, ItemStack stack,
            BlockPos position, EnumFacing side, Vec3 hit, CallbackInfoReturnable<Boolean> callback) {
        if (!ClientEvents.INTERACT.listening())
            return;
        Interaction interaction = new Interaction(player, Interaction.Action.RIGHT_CLICK_BLOCK,
                () -> false);
        ClientEvents.INTERACT.publish(listener -> listener.accept(interaction));
    }

    @Inject(method = "sendUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/NetHandlerPlayClient;addToSendQueue(Lnet/minecraft/network/Packet;)V",
            shift = At.Shift.AFTER))
    private void pit12$interactAir(EntityPlayer player, World world, ItemStack stack,
            CallbackInfoReturnable<Boolean> callback) {
        if (!ClientEvents.INTERACT.listening())
            return;
        Interaction interaction = new Interaction(player, Interaction.Action.RIGHT_CLICK_AIR,
                () -> false);
        ClientEvents.INTERACT.publish(listener -> listener.accept(interaction));
    }
}
