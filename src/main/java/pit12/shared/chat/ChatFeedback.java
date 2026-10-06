/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.shared.chat;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import pit12.bootstrap.BuildConfig;

public final class ChatFeedback {
    private static final String BUILD_LABEL = BuildConfig.RELEASE_BUILD ? BuildConfig.VERSION
            : BuildConfig.GIT_COMMIT.isEmpty() ? "dev"
                    : BuildConfig.GIT_COMMIT.substring(0,
                            Math.min(7, BuildConfig.GIT_COMMIT.length()));

    public enum Tone {
        INFO(EnumChatFormatting.WHITE),
        SUCCESS(EnumChatFormatting.GREEN),
        WARNING(EnumChatFormatting.YELLOW),
        ERROR(EnumChatFormatting.RED);

        private final EnumChatFormatting color;

        Tone(EnumChatFormatting color) {
            this.color = color;
        }
    }

    private ChatFeedback() {}

    public static void reply(ICommandSender sender, Tone tone, String message) {
        ChatComponentText prefix = new ChatComponentText("[");
        prefix.getChatStyle().setColor(EnumChatFormatting.DARK_GRAY);
        ChatComponentText number = new ChatComponentText("12");
        number.getChatStyle().setColor(EnumChatFormatting.DARK_AQUA).setBold(true);
        ChatComponentText name = new ChatComponentText("pit");
        name.getChatStyle().setColor(EnumChatFormatting.AQUA).setBold(true);
        ChatComponentText version = new ChatComponentText(" " + BUILD_LABEL);
        version.getChatStyle().setColor(EnumChatFormatting.GRAY);
        ChatComponentText body = new ChatComponentText(message);
        body.getChatStyle().setColor(tone.color);
        prefix.appendSibling(number).appendSibling(name).appendSibling(version)
                .appendText("] » ")
                .appendSibling(body);
        sender.addChatMessage(prefix);
    }
}
