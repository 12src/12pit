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
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import pit12.platform.lunar.LunarSupport;
import pit12.platform.mixin.accessor.PatcherConfigAccessor;
import pit12.shared.rendering.FontWidths;

@SuppressWarnings("target")
@Mixin(FontRenderer.class)
public abstract class OptiFineFontRendererMixin implements FontWidths.Metrics {
    @Unique
    private static final boolean pit12$LUNAR = LunarSupport.isLoaded();
    @Unique
    private static final boolean pit12$PATCHER = !pit12$LUNAR && Launch.classLoader
            .getResource("club/sk1er/patcher/config/PatcherConfig.class") != null;
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
        boolean patcher = pit12$PATCHER && PatcherConfigAccessor.pit12$optimizedFontRenderer();
        float advance;
        // Lunar routes nonbreaking spaces through its Unicode glyphs.
        if (character == ' ' || character == '\u00a0' && !pit12$LUNAR) {
            advance = unicodeFlag ? 4.0F : charWidthFloat[patcher ? ' ' : character];
        } else if (unicode) {
            int glyph = glyphWidth[character] & 0xFF;
            advance = glyph == 0 ? 0.0F : ((glyph & 15) + 1 - (glyph >>> 4)) / 2.0F + 1.0F;
        } else {
            advance = charWidthFloat[index];
        }
        if (patcher) {
            // PolyPatcher truncates each advance after adding one for bold.
            return (int) (advance + (bold ? 1.0F : 0.0F));
        }
        if (bold) {
            advance += unicode ? 0.5F : offsetBold;
        }
        return advance;
    }
}
