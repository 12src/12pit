package pit12.platform.mixin.feature.tooltip;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pit12.feature.tooltip.HeldItemTooltipBinding;
import pit12.feature.tooltip.TooltipConfig;
import pit12.runtime.item.PitEnchantmentReader;

@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin implements HeldItemTooltipBinding {
    @Shadow
    private ItemStack highlightingItemStack;
    @Unique
    private TooltipConfig pit12$tooltipConfig;

    @Override
    public void pit12$bindTooltipConfig(TooltipConfig config) {
        pit12$tooltipConfig = config;
    }

    @ModifyVariable(method = "renderSelectedItem", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private String pit12$replaceHeldItemName(String originalName) {
        TooltipConfig config = pit12$tooltipConfig;
        if (config == null || !config.enabled() || !config.showEnchantments())
            return originalName;
        String enchantments = PitEnchantmentReader.read(highlightingItemStack)
                .formatBoldDisplayNames(config.showGem());
        return enchantments == null ? originalName : enchantments;
    }

    // The second integer local is the tooltip's Y coordinate.
    @ModifyVariable(method = "renderSelectedItem", at = @At("STORE"), ordinal = 1)
    private int pit12$moveHeldItemTooltipUp(int originalY) {
        TooltipConfig config = pit12$tooltipConfig;
        return config == null || !config.enabled() ? originalY : originalY - config.upwardOffset();
    }
}
