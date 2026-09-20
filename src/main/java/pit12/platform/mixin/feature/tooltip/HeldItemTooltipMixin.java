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
package pit12.platform.mixin.feature.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pit12.feature.tooltip.HeldItemTooltipBinding;
import pit12.feature.tooltip.TooltipConfig;
import pit12.runtime.item.PitEnchantmentReader;

@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class HeldItemTooltipMixin extends GuiIngame implements HeldItemTooltipBinding {
    @Unique
    private TooltipConfig pit12$tooltipConfig;

    protected HeldItemTooltipMixin(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void pit12$bindTooltipConfig(TooltipConfig config) {
        pit12$tooltipConfig = config;
    }

    @ModifyVariable(method = "renderToolHightlight", remap = false,
            at = @At(value = "STORE", ordinal = 0), require = 0)
    private String pit12$replaceHeldItemName(String originalName) {
        TooltipConfig config = pit12$tooltipConfig;
        if (config == null || !config.enabled() || !config.showEnchantments()) {
            return originalName;
        }
        String enchantments =
                PitEnchantmentReader.read(highlightingItemStack).formatBoldDisplayNames();
        return enchantments == null ? originalName : enchantments;
    }

    // Forge stores the shared tooltip Y coordinate in slot 4 before either font-renderer branch.
    @ModifyVariable(method = "renderToolHightlight", remap = false, at = @At("STORE"), index = 4,
            require = 0)
    private int pit12$moveHeldItemTooltipUp(int originalY) {
        TooltipConfig config = pit12$tooltipConfig;
        return config == null || !config.enabled() ? originalY : originalY - config.upwardOffset();
    }
}
