package pit12.platform.mixin.client;

import static org.objectweb.asm.Opcodes.PUTFIELD;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.shared.event.ClientEvents;
import pit12.shared.lifecycle.ClientStartupBinding;

@Mixin(Minecraft.class)
public abstract class OrnitheMinecraftMixin implements ClientStartupBinding {
    @Unique
    private Runnable pit12$startup;

    @Shadow
    public WorldClient theWorld;

    @Override
    public void bindClientStartup(Runnable startup) {
        pit12$startup = startup;
    }

    @Inject(method = "startGame", at = @At("RETURN"))
    private void pit12$startClient(CallbackInfo callback) {
        // Fabric's client entrypoint runs before settings and renderers exist.
        Runnable startup = pit12$startup;
        pit12$startup = null;
        if (startup != null)
            startup.run();
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void pit12$beforeTick(CallbackInfo callback) {
        ClientEvents.INPUT_TICK.publish(Runnable::run);
        ClientEvents.TICK_START.publish(Runnable::run);
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void pit12$afterTick(CallbackInfo callback) {
        ClientEvents.TICK_END.publish(Runnable::run);
    }

    @Inject(method = "dispatchKeypresses", at = @At("RETURN"))
    private void pit12$keyInput(CallbackInfo callback) {
        ClientEvents.KEY_INPUT.publish(Runnable::run);
    }

    @Inject(method = "displayGuiScreen", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;currentScreen:Lnet/minecraft/client/gui/GuiScreen;",
            opcode = PUTFIELD))
    private void pit12$openScreen(GuiScreen screen, CallbackInfo callback) {
        ClientEvents.GUI_OPEN.publish(listener -> listener.accept(screen));
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
            at = @At("HEAD"))
    private void pit12$unloadWorld(WorldClient world, String message, CallbackInfo callback) {
        WorldClient previous = theWorld;
        if (previous != null && previous != world)
            ClientEvents.WORLD_UNLOAD.publish(listener -> listener.accept(previous));
    }
}
