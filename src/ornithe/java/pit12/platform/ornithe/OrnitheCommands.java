package pit12.platform.ornithe;

import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import pit12.feature.commandfix.CommandFixBinding;
import pit12.feature.commandfix.CommandFixConfig;

public final class OrnitheCommands extends CommandHandler implements CommandFixBinding {
    public static final OrnitheCommands INSTANCE = new OrnitheCommands();
    private CommandFixConfig config;

    private OrnitheCommands() {}

    @Override
    public void pit12$bindCommandFixConfig(CommandFixConfig config) {
        this.config = config;
    }

    @Override
    public int executeCommand(ICommandSender sender, String message) {
        if (config != null && config.enabled() && !message.startsWith("/"))
            return 0;
        String input = message.startsWith("/") ? message.substring(1) : message;
        int space = input.indexOf(' ');
        if (!getCommands().containsKey(space < 0 ? input : input.substring(0, space)))
            return 0;
        super.executeCommand(sender, message);
        // A recognized command also consumes the message when execution reports an error.
        return 1;
    }
}
