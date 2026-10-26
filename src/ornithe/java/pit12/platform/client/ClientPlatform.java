package pit12.platform.client;

import net.fabricmc.loader.api.FabricLoader;
import pit12.feature.commandfix.CommandFixBinding;
import pit12.platform.command.ClientCommandAdapter;
import pit12.platform.lwjgl.LwjglInputDevice;
import pit12.platform.ornithe.OrnitheCommands;
import pit12.platform.pylon.PylonInputDevice;
import pit12.runtime.command.CommandNode;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.input.InputDevice;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ClientPlatform {
    private ClientPlatform() {}

    public static InputDevice inputDevice() {
        return FabricLoader.getInstance().isModLoaded("pylon")
                ? new PylonInputDevice() : new LwjglInputDevice();
    }

    public static Runnable registerCommand(CommandRegistry registry, CommandNode command) {
        return ClientCommandAdapter.register(OrnitheCommands.INSTANCE, registry, command);
    }

    public static CommandFixBinding commandFix() {
        return OrnitheCommands.INSTANCE;
    }

    public static boolean hasOptifine() {
        return ClientPlatform.class.getClassLoader()
                .getResource("optifine/OptiFineClassTransformer.class") != null;
    }

    public static boolean renderHotbar() {
        return true;
    }

    public static ClientLifecycle integration(ConfigCatalog configs) {
        // Ornithe installs the game hooks through Mixins.
        return null;
    }
}
