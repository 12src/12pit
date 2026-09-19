/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.feature.hudeditor;

import net.minecraft.client.Minecraft;
import pit12.feature.Feature;
import pit12.feature.hudeditor.api.HudEditor;
import pit12.runtime.hud.HudRegistry;

public final class HudEditorFeature implements Feature, HudEditor {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final HudEditorController controller;
    private boolean started;

    public HudEditorFeature(HudRegistry registry) {
        controller = new HudEditorController(registry);
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void stop() {
        started = false;
        if (minecraft.currentScreen instanceof HudEditorScreen
                && ((HudEditorScreen) minecraft.currentScreen).belongsTo(controller)) {
            minecraft.displayGuiScreen(null);
        }
        controller.dispose();
    }

    @Override
    public void open() {
        if (!started) {
            return;
        }
        if (minecraft.currentScreen instanceof HudEditorScreen
                && ((HudEditorScreen) minecraft.currentScreen).belongsTo(controller)) {
            return;
        }
        minecraft.displayGuiScreen(new HudEditorScreen(controller, minecraft.currentScreen));
    }
}
