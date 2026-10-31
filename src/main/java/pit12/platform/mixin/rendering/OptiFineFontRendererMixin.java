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
package pit12.platform.mixin.rendering;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pit12.shared.rendering.FontWidths;

@SuppressWarnings("target")
@Mixin(FontRenderer.class)
public abstract class OptiFineFontRendererMixin implements FontWidths.Metrics {
    @Shadow
    private boolean unicodeFlag;
    @Shadow
    private byte[] glyphWidth;
    @Shadow(remap = false)
    private float[] charWidthFloat;
    @Shadow(remap = false)
    public float offsetBold;

    @Override
    public float pit12$charAdvance(char character, boolean bold) {
        // OptiFine's width methods round glyphs and overcount Unicode bold.
        int index = FontWidths.FONT_CHARACTERS.indexOf(character);
        boolean unicode = unicodeFlag || index < 0;
        float advance;
        if (character == ' ' || character == '\u00a0') {
            advance = unicodeFlag ? 4.0F : charWidthFloat[character];
        } else if (unicode) {
            int glyph = glyphWidth[character] & 0xFF;
            advance = glyph == 0 ? 0.0F : ((glyph & 15) + 1 - (glyph >>> 4)) / 2.0F + 1.0F;
        } else {
            advance = charWidthFloat[index];
        }
        if (bold) {
            advance += unicode ? 0.5F : offsetBold;
        }
        return advance;
    }
}
