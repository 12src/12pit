package pit12.platform.mixin.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.ICommandSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.platform.ornithe.OrnitheCommands;

@Mixin(EntityPlayerSP.class)
public abstract class OrnitheEntityPlayerSPMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void pit12$clientCommand(String message, CallbackInfo callback) {
        if (OrnitheCommands.INSTANCE.executeCommand((ICommandSender) this, message) != 0)
            callback.cancel();
    }
}
