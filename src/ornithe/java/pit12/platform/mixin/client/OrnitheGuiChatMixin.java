package pit12.platform.mixin.client;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.platform.ornithe.OrnitheCommands;

@Mixin(GuiChat.class)
public abstract class OrnitheGuiChatMixin extends GuiScreen {
    @Unique
    private String pit12$completionInput;

    @Inject(method = "sendAutocompleteRequest", at = @At("HEAD"))
    private void pit12$completeCommand(String input, String word, CallbackInfo callback) {
        pit12$completionInput = input;
    }

    @ModifyVariable(method = "onAutocompleteResponse", at = @At("HEAD"), argsOnly = true)
    private String[] pit12$addClientCompletions(String[] response) {
        String input = pit12$completionInput;
        if (input == null || !input.startsWith("/"))
            return response;
        List<String> completions = OrnitheCommands.INSTANCE.getTabCompletionOptions(mc.thePlayer,
                input.substring(1), mc.thePlayer.getPosition());
        if (completions == null || completions.isEmpty())
            return response;
        if (input.indexOf(' ') < 0)
            completions.replaceAll(name -> "/" + name);
        Set<String> merged = new TreeSet<>(Arrays.asList(response));
        merged.addAll(completions);
        return merged.toArray(new String[0]);
    }
}
