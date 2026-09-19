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
package pit12.runtime.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class FeatureConfig {
    private final String id;
    private final String displayName;
    private final ConfigCategory category;
    private final String description;
    private final BooleanSetting enabled;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final List<Setting<?>> options = new ArrayList<Setting<?>>();
    private final List<HudConfig> huds = new ArrayList<HudConfig>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private final List<Setting<?>> optionsView = Collections.unmodifiableList(options);
    private final List<HudConfig> hudsView = Collections.unmodifiableList(huds);
    private final Map<String, Setting<?>> settingsById = new LinkedHashMap<String, Setting<?>>();
    private final Map<String, HudConfig> hudsById = new LinkedHashMap<String, HudConfig>();

    protected FeatureConfig(String id, String displayName, ConfigCategory category,
            String description) {
        this(id, displayName, category, description, true);
    }

    protected FeatureConfig(String id, String displayName, ConfigCategory category,
            String description, boolean toggleable) {
        this.id = ConfigNames.requireStableId(id, "feature id");
        this.displayName = ConfigNames.requireText(displayName, "feature display name");
        this.category = Objects.requireNonNull(category, "category");
        this.description = ConfigNames.requireDescription(description);
        enabled =
                toggleable
                        ? new BooleanSetting("enabled", "Enabled",
                                "Enables " + this.displayName + ".", true)
                        : null;
        if (enabled != null) {
            register(enabled, false);
        }
    }

    protected final BooleanSetting booleanSetting(String id, String displayName, String description,
            boolean defaultValue) {
        BooleanSetting setting = new BooleanSetting(id, displayName, description, defaultValue);
        register(setting, true);
        return setting;
    }

    protected final KeybindSetting keybindSetting(String id, String displayName, String description,
            int defaultValue) {
        KeybindSetting setting = new KeybindSetting(id, displayName, description, defaultValue);
        register(setting, true);
        return setting;
    }

    protected final ColorSetting colorSetting(String id, String displayName, String description,
            int defaultValue) {
        ColorSetting setting = new ColorSetting(id, displayName, description, defaultValue);
        register(setting, true);
        return setting;
    }

    protected final HudConfig hudConfig(String id, String displayName, HudAnchor defaultAnchor,
            int defaultOffsetX, int defaultOffsetY, boolean defaultTextShadow) {
        String hudId = ConfigNames.requireStableId(id, "HUD id");
        String hudDisplayName = ConfigNames.requireText(displayName, "HUD display name");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
        if (hudsById.containsKey(hudId)) {
            throw new IllegalArgumentException(
                    "Duplicate HUD id " + hudId + " in feature " + this.id);
        }
        String prefix = hudId + ".";
        BooleanSetting textShadow = new BooleanSetting(prefix + "text_shadow", "Text shadow",
                "Draws a shadow behind text in the " + hudDisplayName + ".", defaultTextShadow);
        IntegerSetting anchor = new IntegerSetting(prefix + "anchor", "Anchor", "",
                defaultAnchor.id(), HudAnchor.TOP_LEFT.id(), HudAnchor.BOTTOM_RIGHT.id());
        IntegerSetting offsetX = new IntegerSetting(prefix + "offset_x", "Horizontal offset", "",
                defaultOffsetX, -32768, 32767);
        IntegerSetting offsetY = new IntegerSetting(prefix + "offset_y", "Vertical offset", "",
                defaultOffsetY, -32768, 32767);
        IntegerSetting scale = new IntegerSetting(prefix + "scale", "Scale", "", 100, 25, 300);
        register(textShadow, true);
        register(anchor, false);
        register(offsetX, false);
        register(offsetY, false);
        register(scale, false);
        HudConfig hud =
                new HudConfig(hudId, hudDisplayName, textShadow, anchor, offsetX, offsetY, scale);
        huds.add(hud);
        hudsById.put(hudId, hud);
        return hud;
    }

    private void register(Setting<?> setting, boolean option) {
        if (settingsById.containsKey(setting.id())) {
            throw new IllegalArgumentException(
                    "Duplicate setting id " + setting.id() + " in feature " + id);
        }
        settings.add(setting);
        if (option) {
            options.add(setting);
        }
        settingsById.put(setting.id(), setting);
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final ConfigCategory category() {
        return category;
    }

    public final String description() {
        return description;
    }

    public final boolean toggleable() {
        return enabled != null;
    }

    public final boolean enabled() {
        return enabled == null || enabled.get().booleanValue();
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == null) {
            throw new IllegalStateException("Feature cannot be disabled: " + id);
        }
        this.enabled.set(Boolean.valueOf(enabled));
    }

    public final List<Setting<?>> settings() {
        return settingsView;
    }

    public final List<Setting<?>> options() {
        return optionsView;
    }

    public final List<HudConfig> huds() {
        return hudsView;
    }

    public final HudConfig hud(String hudId) {
        return hudsById.get(hudId);
    }

    public final Setting<?> setting(String settingId) {
        return settingsById.get(settingId);
    }
}
