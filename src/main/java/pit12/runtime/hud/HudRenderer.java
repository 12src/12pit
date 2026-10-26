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
package pit12.runtime.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import pit12.runtime.config.HudConfig;
import pit12.shared.rendering.UiRenderState;
import pit12.shared.rendering.UiRenderer;

public final class HudRenderer extends UiRenderer {
    private final UiRenderState state = new UiRenderState();

    public HudRenderer() {
        super(Minecraft.getMinecraft());
    }

    public void text(String text, int x, int y, int color, HudConfig config) {
        fontRenderer.drawString(text, x, y, color, config.textShadow().get());
    }

    public static HudBounds layout(HudElement element, int screenWidth, int screenHeight,
            float pixelScale, boolean editing) {
        float scale = element.config().scaleFactor();
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        boolean unicode = font.getUnicodeFlag();
        ((HudFontBinding) font).pit12$setHudUnicode(element.config().font().get() == 1);
        try {
            element.prepare(pixelScale * scale, editing);
        } finally {
            ((HudFontBinding) font).pit12$setHudUnicode(unicode);
        }
        int width = Math.max(1, Math.round(Math.max(1, element.width()) * scale));
        int height = Math.max(1, Math.round(Math.max(1, element.height()) * scale));
        int x = Math.max(0, Math.min(Math.max(0, screenWidth - width),
                element.config().resolveX(screenWidth, width)));
        int y = Math.max(0, Math.min(Math.max(0, screenHeight - height),
                element.config().resolveY(screenHeight, height)));
        return new HudBounds(x, y, width, height, scale);
    }

    public void render(HudElement element, HudBounds bounds, float partialTicks, boolean editing) {
        state.begin();
        boolean unicode = fontRenderer.getUnicodeFlag();
        ((HudFontBinding) fontRenderer).pit12$setHudUnicode(element.config().font().get() == 1);
        try {
            GlStateManager.translate(bounds.x, bounds.y, 0.0F);
            GlStateManager.scale(bounds.scale, bounds.scale, 1.0F);
            element.render(partialTicks, editing);
        } finally {
            ((HudFontBinding) fontRenderer).pit12$setHudUnicode(unicode);
            state.end();
        }
    }
}
