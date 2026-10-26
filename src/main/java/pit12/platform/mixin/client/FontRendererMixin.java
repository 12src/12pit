package pit12.platform.mixin.client;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pit12.runtime.hud.HudFontBinding;

@Mixin(FontRenderer.class)
public abstract class FontRendererMixin implements HudFontBinding {
    @Shadow
    private boolean unicodeFlag;

    @Shadow
    public abstract void setUnicodeFlag(boolean unicode);

    @Override
    public void pit12$setHudUnicode(boolean unicode) {
        if (unicodeFlag == unicode)
            return;
        // Argentum reloads chunks when the setter sees a mode change. Keep its font cache callbacks.
        unicodeFlag = unicode;
        setUnicodeFlag(unicode);
    }
}
