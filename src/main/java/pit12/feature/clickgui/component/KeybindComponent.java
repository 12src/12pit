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
package pit12.feature.clickgui.component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import org.lwjgl.input.Keyboard;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;

public final class KeybindComponent extends GuiComponent {
    private static final int PREFERRED_HEIGHT = 12;
    private static final int MINIMUM_WIDTH = 12;
    private final IntSupplier keyCode;
    private final IntConsumer setter;
    private boolean capturing;
    private int surfaceColor = ClickGuiTheme.ROW;

    public KeybindComponent(IntSupplier keyCode, IntConsumer setter) {
        super(0, 0, MINIMUM_WIDTH, PREFERRED_HEIGHT);
        this.keyCode = keyCode;
        this.setter = setter;
    }

    public int preferredWidth(ClickGuiRenderer renderer) {
        if (keyCode.getAsInt() == Keyboard.KEY_NONE) {
            return MINIMUM_WIDTH;
        }
        return Math.min(38, Math.max(MINIMUM_WIDTH, renderer.textWidth(keyName(), 6.0F) + 6));
    }

    @Override
    public int preferredHeight() {
        return PREFERRED_HEIGHT;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public void setSurfaceColor(int surfaceColor) {
        this.surfaceColor = surfaceColor;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        boolean hovered = contains(mouseX, mouseY);
        boolean populated = keyCode.getAsInt() != Keyboard.KEY_NONE;
        int opaqueBackground = ClickGuiTheme.contrastingColor(surfaceColor);
        int foreground;
        if (capturing || populated) {
            int background = ClickGuiTheme.withAlpha(opaqueBackground,
                    capturing ? 232 : hovered ? 216 : 192);
            foreground = ClickGuiTheme.contrastingColor(opaqueBackground);
            renderer.roundedRect(x, y, width, height, 4, background);
        } else {
            foreground = ClickGuiTheme.contrastingColor(surfaceColor);
        }
        if (capturing) {
            renderer.centeredTexture(TextureIcon.CLOSE, x + (width - 6) / 2.0F, y, height, 6, 6,
                    ClickGuiTheme.withAlpha(foreground, 160));
        } else if (!populated) {
            renderer.centeredTexture(TextureIcon.BIND, x + (width - 5) / 2.0F, y, height, 5, 5,
                    foreground);
        } else if (hovered) {
            renderer.centeredTexture(TextureIcon.EDIT, x + (width - 5) / 2.0F, y, height, 5, 5,
                    foreground);
        } else {
            renderer.centeredText(renderer.ellipsize(keyName(), width - 4, 6.0F), x, y, width,
                    height, 6.0F, foreground);
        }
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        if (capturing) {
            setter.accept(Keyboard.KEY_NONE);
            capturing = false;
            controller.focus(null);
        } else {
            capturing = true;
            controller.focus(this);
        }
        return true;
    }

    @Override
    public boolean keyTyped(ClickGuiController controller, char character, int keyCode) {
        if (!capturing) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            capturing = false;
            controller.focus(null);
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            setter.accept(Keyboard.KEY_NONE);
        } else if (keyCode != Keyboard.KEY_NONE) {
            setter.accept(keyCode);
        }
        capturing = false;
        controller.focus(null);
        return true;
    }

    @Override
    public void focusChanged(boolean focused) {
        if (!focused) {
            capturing = false;
        }
    }

    private String keyName() {
        String name = Keyboard.getKeyName(keyCode.getAsInt());
        return name == null ? "NONE" : name;
    }
}
