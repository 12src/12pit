package pit12.platform.client;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import pit12.feature.commandfix.CommandFixBinding;
import pit12.platform.command.ClientCommandAdapter;
import pit12.platform.forge.ForgeEvents;
import pit12.platform.lwjgl.LwjglInputDevice;
import pit12.runtime.command.CommandNode;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.input.InputDevice;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ClientPlatform {
    private ClientPlatform() {}

    public static InputDevice inputDevice() {
        return new LwjglInputDevice();
    }

    public static Runnable registerCommand(CommandRegistry registry, CommandNode command) {
        return ClientCommandAdapter.register(ClientCommandHandler.instance, registry, command);
    }

    public static CommandFixBinding commandFix() {
        return (CommandFixBinding) ClientCommandHandler.instance;
    }

    public static boolean hasOptifine() {
        return FMLClientHandler.instance().hasOptifine();
    }

    public static boolean renderHotbar() {
        return GuiIngameForge.renderHotbar;
    }

    public static ClientLifecycle integration(ConfigCatalog configs) {
        return new ForgeEvents(configs);
    }
}
