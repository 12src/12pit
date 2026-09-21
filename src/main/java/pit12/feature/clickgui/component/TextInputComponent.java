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
package pit12.feature.clickgui.component;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiTheme;

public final class TextInputComponent extends GuiComponent {
    private final String placeholder;
    private final Runnable submit;
    private String text = "";
    private int cursor;
    private int maximumLength = 48;
    private boolean focused;
    private boolean selectAll;
    private boolean chromeVisible = true;

    public TextInputComponent(int x, int y, int width, int height, String placeholder,
            Runnable submit) {
        super(x, y, width, height);
        this.placeholder = placeholder;
        this.submit = submit;
    }

    public String text() {
        return text;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text.substring(0, Math.min(text.length(), maximumLength));
        cursor = this.text.length();
        selectAll = false;
    }

    public void setMaximumLength(int maximumLength) {
        this.maximumLength = Math.max(0, maximumLength);
        setText(text);
    }

    public void setChromeVisible(boolean chromeVisible) {
        this.chromeVisible = chromeVisible;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        if (chromeVisible) {
            renderer.roundedRect(x, y, width, height, 4, ClickGuiTheme.PANEL_INSET);
            renderer.roundedOutline(x, y, width, height, 4.0F, 0.75F,
                    focused ? ClickGuiTheme.ACCENT : ClickGuiTheme.BORDER);
        }
        String displayed = text.isEmpty() && !focused ? placeholder : text;
        int color = text.isEmpty() && !focused ? ClickGuiTheme.MUTED_TEXT : ClickGuiTheme.TEXT;
        int inset = chromeVisible ? 4 : 3;
        renderer.verticallyCenteredText(renderer.ellipsize(displayed, width - inset * 2, 8.0F),
                x + inset, y, height, 8.0F, color);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            String prefix = text.substring(0, cursor);
            int cursorX = Math.min(x + width - inset, x + inset + renderer.textWidth(prefix, 8.0F));
            int cursorHeight = Math.max(5, renderer.fontHeight(8.0F) - 2);
            int cursorY = y + Math.round((height - cursorHeight) / 2.0F);
            renderer.rect(cursorX, cursorY, 1, cursorHeight, ClickGuiTheme.TEXT);
        }
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        controller.focus(this);
        cursor = text.length();
        selectAll = false;
        return true;
    }

    @Override
    public boolean keyTyped(ClickGuiController controller, char character, int keyCode) {
        if (!focused) {
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit.run();
            return true;
        }
        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            cursor = text.length();
            selectAll = true;
            return true;
        }
        if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            GuiScreen.setClipboardString(text);
            return true;
        }
        if (GuiScreen.isKeyComboCtrlX(keyCode)) {
            GuiScreen.setClipboardString(text);
            text = "";
            cursor = 0;
            selectAll = false;
            return true;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            insert(ChatAllowedCharacters.filterAllowedCharacters(GuiScreen.getClipboardString()));
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK && cursor > 0) {
            if (selectAll) {
                text = "";
                cursor = 0;
                selectAll = false;
                return true;
            }
            text = text.substring(0, cursor - 1) + text.substring(cursor);
            cursor--;
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (selectAll) {
                text = "";
                cursor = 0;
                selectAll = false;
                return true;
            }
            if (cursor < text.length()) {
                text = text.substring(0, cursor) + text.substring(cursor + 1);
                return true;
            }
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            cursor = Math.max(0, cursor - 1);
            selectAll = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            cursor = Math.min(text.length(), cursor + 1);
            selectAll = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            cursor = 0;
            selectAll = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            cursor = text.length();
            selectAll = false;
            return true;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(character)) {
            insert(Character.toString(character));
            return true;
        }
        return false;
    }

    @Override
    public void focusChanged(boolean focused) {
        this.focused = focused;
    }

    private void insert(String addition) {
        if (selectAll) {
            text = "";
            cursor = 0;
            selectAll = false;
        }
        int allowed = maximumLength - text.length();
        if (allowed <= 0 || addition.isEmpty()) {
            return;
        }
        String accepted = addition.substring(0, Math.min(allowed, addition.length()));
        text = text.substring(0, cursor) + accepted + text.substring(cursor);
        cursor += accepted.length();
    }
}
