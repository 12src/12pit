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
package pit12.platform.mixin.feature.fontcachefix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pit12.feature.fontcachefix.FontCacheFixBinding;

@SuppressWarnings("target")
@Mixin(FontRenderer.class)
public abstract class LunarFontRendererMixin {
    @Shadow
    private boolean unicodeFlag;

    // Lunar obfuscates the cache class and method names, so match the lookup by its position.
    @Dynamic("Lunar's font cache lookup")
    @ModifyArg(method = "lunar$renderStringAtPos(Ljava/lang/String;ZI)V",
            at = @At(value = "INVOKE", ordinal = 0), index = 0, remap = false, require = 1)
    private String pit12$fontCacheLookup(String text) {
        if (!((FontCacheFixBinding) Minecraft.getMinecraft()).pit12$fontCacheFixEnabled()) {
            return text;
        }
        return (unicodeFlag ? "\u0001" : "\u0000") + text;
    }

    @Dynamic("Lunar's font cache key constructor")
    @ModifyArg(method = "lunar$renderStringAtPos(Ljava/lang/String;ZI)V",
            at = @At(value = "INVOKE", target = "<init>(Ljava/lang/String;IZ)V"), index = 0,
            remap = false, require = 1)
    private String pit12$fontCacheEntry(String text) {
        return pit12$fontCacheLookup(text);
    }
}
