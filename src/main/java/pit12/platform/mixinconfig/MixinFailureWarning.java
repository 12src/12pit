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
package pit12.platform.mixinconfig;

import static net.minecraft.util.EnumChatFormatting.RED;
import static net.minecraft.util.EnumChatFormatting.WHITE;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import pit12.runtime.languages.Languages;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.lifecycle.ClientLifecycle;

public final class MixinFailureWarning implements ClientLifecycle {
    private final Minecraft minecraft;
    private final ClientSession session;
    private final Languages language;
    private final Runnable sessionChanged = this::onSessionChanged;
    private WorldClient world;

    public MixinFailureWarning(Minecraft minecraft, ClientSession session, Languages language) {
        this.minecraft = minecraft;
        this.session = session;
        this.language = language;
    }

    @Override
    public void start() {
        session.addListener(sessionChanged);
        onSessionChanged();
    }

    @Override
    public void stop() {
        session.removeListener(sessionChanged);
        world = null;
    }

    private void onSessionChanged() {
        WorldClient nextWorld = session.world();
        if (world == nextWorld) {
            return;
        }
        world = nextWorld;
        if (world == null || minecraft.thePlayer == null) {
            return;
        }
        List<String> failedMixins = MixinConfigPlugin.failedMixinNames();
        if (failedMixins.isEmpty()) {
            return;
        }
        ChatFeedback.reply(minecraft.thePlayer, Tone.ERROR, language.format(
                "{0} failed to apply: {1}. This may cause unexpected behavior. If you are unsure how this may affect the game, we recommend reporting this issue with your logs.",
                Tone.ERROR.argument(RED,
                        language.translate(failedMixins.size() == 1 ? "A mixin" : "Some mixins")),
                Tone.ERROR.argument(WHITE, String.join(", ", failedMixins))));
    }
}
