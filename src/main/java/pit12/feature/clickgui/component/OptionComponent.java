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

import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.NumberSetting;
import pit12.runtime.config.Setting;

public final class OptionComponent {
    private static final int TOGGLE_HEIGHT = 18;
    private static final int KEYBIND_HEIGHT = 22;
    private final ConfigOption<?> option;
    private final GuiComponent component;

    public OptionComponent(ConfigOption<?> option) {
        this.option = option;
        Setting<?> setting = option.setting();
        switch (option.kind()) {
            case BOOLEAN:
                BooleanSetting booleanSetting = (BooleanSetting) setting;
                component = new ToggleComponent(0, 0, 0, TOGGLE_HEIGHT, setting.displayName(),
                        () -> booleanSetting.get().booleanValue(), booleanSetting::set);
                break;
            case NUMBER:
                component = new SliderComponent((NumberSetting<?>) setting);
                break;
            case COLOR:
                IntegerSetting colorSetting = (IntegerSetting) setting;
                ColorPickerComponent colorPicker = new ColorPickerComponent(setting.displayName(),
                        () -> colorSetting.get().intValue(),
                        color -> colorSetting.set(Integer.valueOf(color)));
                component = colorPicker;
                break;
            case KEYBIND:
                IntegerSetting keybindSetting = (IntegerSetting) setting;
                component = new KeybindRowComponent(0, 0, 0, KEYBIND_HEIGHT, setting.displayName(),
                        () -> keybindSetting.get().intValue(),
                        keyCode -> keybindSetting.set(Integer.valueOf(keyCode)));
                break;
            default:
                throw new IllegalArgumentException("Unsupported option kind " + option.kind());
        }
    }

    public Setting<?> setting() {
        return option.setting();
    }

    public int preferredHeight() {
        return component.preferredHeight();
    }

    public int maximumHeight() {
        return component.maximumHeight();
    }

    public void setBounds(int x, int y, int width) {
        component.setBounds(x, y, width, preferredHeight());
    }

    public boolean contains(int mouseX, int mouseY) {
        return component.contains(mouseX, mouseY);
    }

    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        component.render(renderer, mouseX, mouseY, partialTicks);
    }

    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        return component.mousePressed(controller, mouseX, mouseY, button);
    }

    public void close() {
        component.close();
    }
}
