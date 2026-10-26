package pit12;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import pit12.bootstrap.ClientBootstrap;
import pit12.shared.lifecycle.ClientStartupBinding;

public final class Pit12 implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ((ClientStartupBinding) Minecraft.getMinecraft())
                .bindClientStartup(() -> new ClientBootstrap().start());
    }
}
