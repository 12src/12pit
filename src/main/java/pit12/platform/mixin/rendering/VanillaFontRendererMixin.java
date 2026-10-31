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

@Mixin(FontRenderer.class)
public abstract class VanillaFontRendererMixin implements FontWidths.Metrics {
    @Shadow
    private boolean unicodeFlag;
    @Shadow
    private int[] charWidth;
    @Shadow
    private byte[] glyphWidth;

    @Override
    public float pit12$charAdvance(char character, boolean bold) {
        float advance;
        if (character == ' ') {
            advance = 4.0F;
        } else {
            int index = FontWidths.FONT_CHARACTERS.indexOf(character);
            if (!unicodeFlag && index >= 0) {
                advance = charWidth[index];
            } else {
                // Vanilla's width getter expands wide Unicode glyphs to nine pixels.
                int glyph = glyphWidth[character];
                advance = glyph == 0 ? 0.0F : ((glyph & 15) + 1.0F - (glyph >>> 4)) / 2.0F + 1.0F;
            }
        }
        return (int) (advance + (bold ? 1.0F : 0.0F));
    }
}
