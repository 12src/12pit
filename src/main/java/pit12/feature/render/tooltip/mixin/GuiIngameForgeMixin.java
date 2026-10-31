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
package pit12.feature.render.tooltip.mixin;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.common.util.Constants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pit12.feature.render.tooltip.HeldItemTooltipBinding;
import pit12.feature.render.tooltip.TooltipConfig;
import pit12.runtime.item.PitEnchantmentReader;

@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeMixin extends GuiIngame implements HeldItemTooltipBinding {
    @Unique
    private TooltipConfig pit12$tooltipConfig;
    @Unique
    private NBTTagList pit12$tooltipEnchantments;
    @Unique
    private boolean pit12$tooltipGemmed;
    @Unique
    private boolean pit12$tooltipShowGem;
    @Unique
    private String pit12$tooltipText;

    protected GuiIngameForgeMixin(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void pit12$bindTooltipConfig(TooltipConfig config) {
        pit12$tooltipConfig = config;
        pit12$tooltipEnchantments = null;
        pit12$tooltipText = null;
    }

    @ModifyVariable(method = "renderToolHightlight", remap = false,
            at = @At(value = "STORE", ordinal = 0))
    private String pit12$replaceHeldItemName(String originalName) {
        TooltipConfig config = pit12$tooltipConfig;
        if (config == null || !config.enabled() || !config.showEnchantments()) {
            return originalName;
        }
        NBTTagCompound tag =
                highlightingItemStack == null ? null : highlightingItemStack.getTagCompound();
        NBTTagCompound extra =
                tag != null && tag.hasKey("ExtraAttributes", Constants.NBT.TAG_COMPOUND)
                        ? tag.getCompoundTag("ExtraAttributes")
                        : null;
        NBTTagList enchantments =
                extra != null && extra.hasKey("CustomEnchants", Constants.NBT.TAG_LIST)
                        ? extra.getTagList("CustomEnchants", Constants.NBT.TAG_COMPOUND)
                        : null;
        boolean showGem = config.showGem();
        boolean gemmed = showGem && extra != null && extra.getInteger("UpgradeGemsUses") > 0;
        if (showGem != pit12$tooltipShowGem || gemmed != pit12$tooltipGemmed
                || !Objects.equals(enchantments, pit12$tooltipEnchantments)) {
            // Item NBT can change in place, so keep a separate snapshot for comparisons.
            pit12$tooltipEnchantments =
                    enchantments == null ? null : (NBTTagList) enchantments.copy();
            pit12$tooltipShowGem = showGem;
            pit12$tooltipGemmed = gemmed;
            pit12$tooltipText = PitEnchantmentReader.read(highlightingItemStack)
                    .formatBoldDisplayNames(showGem);
        }
        return pit12$tooltipText == null ? originalName : pit12$tooltipText;
    }

    // Slot 4 holds the tooltip Y coordinate before either font renderer is chosen.
    @ModifyVariable(method = "renderToolHightlight", remap = false, at = @At("STORE"), index = 4)
    private int pit12$moveHeldItemTooltipUp(int originalY) {
        TooltipConfig config = pit12$tooltipConfig;
        return config == null || !config.enabled() ? originalY : originalY - config.upwardOffset();
    }
}
