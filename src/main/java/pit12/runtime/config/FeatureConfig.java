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
package pit12.runtime.config;

import static pit12.runtime.languages.Languages.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import pit12.runtime.languages.Languages;

public abstract class FeatureConfig {
    protected enum HudGroup {
        AUTO, SUBCATEGORY, SUBSUBCATEGORY
    }

    private final String id;
    private final String originalDisplayName;
    private String displayName;
    private final ConfigCategory category;
    private final String originalDescription;
    private String description;
    private final BooleanSetting enabled;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final List<ConfigOption<?>> options = new ArrayList<ConfigOption<?>>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private final List<ConfigOption<?>> optionsView = Collections.unmodifiableList(options);
    private final Set<String> settingIds = new HashSet<String>();
    private ConfigGroup currentSubcategory;
    private ConfigGroup currentSubsubcategory;
    private boolean hasSubcategories;
    private final List<Runnable> hudGroups = new ArrayList<>();

    protected FeatureConfig(String id, String displayName, ConfigCategory category,
            String description) {
        this(id, displayName, category, description, true);
    }

    protected FeatureConfig(String id, String displayName, ConfigCategory category,
            String description, boolean toggleable) {
        this(id, displayName, category, description, toggleable, true);
    }

    protected FeatureConfig(String id, String displayName, ConfigCategory category,
            String description, boolean toggleable, boolean defaultEnabled) {
        this.id = ConfigNames.requireStableId(id, "feature id");
        originalDisplayName = displayName;
        this.displayName = displayName;
        this.category = category;
        originalDescription = description;
        this.description = description;
        enabled =
                toggleable
                        ? new BooleanSetting("enabled", source("Enabled"),
                                source("Turns this feature on."), defaultEnabled)
                        : null;
        if (enabled != null) {
            register(enabled, null);
        }
    }

    protected final BooleanSetting booleanSetting(String id, String displayName, String description,
            boolean defaultValue) {
        BooleanSetting setting = new BooleanSetting(id, displayName, description, defaultValue);
        register(setting, ConfigOption.Kind.BOOLEAN);
        return setting;
    }

    protected final IntegerSetting keybindSetting(String id, String displayName, String description,
            int defaultValue) {
        IntegerSetting setting =
                new IntegerSetting(id, displayName, description, defaultValue, 0, 255);
        register(setting, ConfigOption.Kind.KEYBIND);
        return setting;
    }

    protected final IntegerSetting colorSetting(String id, String displayName, String description,
            int defaultValue) {
        IntegerSetting setting =
                new IntegerSetting(id, displayName, description, defaultValue, 0, 0xFFFFFF);
        register(setting, ConfigOption.Kind.COLOR);
        return setting;
    }

    /** The default color uses ARGB (0xAARRGGBB). */
    protected final ColorSetting colorPickerSetting(String id, String displayName,
            String description, int defaultValue) {
        ColorSetting setting = new ColorSetting(id, displayName, description, defaultValue);
        register(setting, ConfigOption.Kind.COLOR_PICKER);
        return setting;
    }

    protected final IntegerSetting integerSliderSetting(String id, String displayName,
            String description, int defaultValue, int minimum, int maximum, int step) {
        IntegerSetting setting = new IntegerSetting(id, displayName, description, defaultValue,
                minimum, maximum, step);
        register(setting, ConfigOption.Kind.NUMBER);
        return setting;
    }

    protected final DoubleSetting doubleSliderSetting(String id, String displayName,
            String description, double defaultValue, double minimum, double maximum, double step) {
        DoubleSetting setting = new DoubleSetting(id, displayName, description, defaultValue,
                minimum, maximum, step);
        register(setting, ConfigOption.Kind.NUMBER);
        return setting;
    }

    protected final ChoiceSetting choiceSetting(String id, String displayName, String description,
            int defaultValue, ChoiceSetting.Choice... choices) {
        ChoiceSetting setting =
                new ChoiceSetting(id, displayName, description, defaultValue, choices);
        register(setting, ConfigOption.Kind.CHOICE);
        return setting;
    }

    protected final void subcategory(String id, String displayName) {
        hasSubcategories = true;
        currentSubcategory = new ConfigGroup(id, displayName);
        currentSubsubcategory = null;
    }

    protected final void subsubcategory(String id, String displayName) {
        currentSubsubcategory = new ConfigGroup(id, displayName);
    }

    protected final HudConfig hudConfig(String id, HudAnchor defaultAnchor, int defaultOffsetX,
            int defaultOffsetY, boolean defaultTextShadow) {
        return hudConfig(id, defaultAnchor, defaultOffsetX, defaultOffsetY, defaultTextShadow,
                HudGroup.AUTO, source("HUD"));
    }

    protected final HudConfig hudConfig(String id, HudAnchor defaultAnchor, int defaultOffsetX,
            int defaultOffsetY, boolean defaultTextShadow, HudGroup grouping, String groupName) {
        String prefix = id + ".";
        BooleanSetting textShadow =
                new BooleanSetting(prefix + "text_shadow", source("Text shadow"),
                        source("Draws a shadow behind HUD text."), defaultTextShadow);
        BooleanSetting useMonospaceFont =
                new BooleanSetting(prefix + "use_monospace_font", source("Use monospace font"),
                        source("Uses the bundled Monocraft font for HUD text."), false);
        BooleanSetting translateText =
                new BooleanSetting(prefix + "translate_text", source("Translate text"),
                        source("Translates HUD text into the selected language."), true);
        ChoiceSetting anchor = new ChoiceSetting(prefix + "anchor", source("Anchor"),
                source("Auto chooses an anchor when the HUD is moved."), HudConfig.AUTO_ANCHOR,
                new ChoiceSetting.Choice(HudConfig.AUTO_ANCHOR, source("Auto")),
                new ChoiceSetting.Choice(0, source("Top left")),
                new ChoiceSetting.Choice(1, source("Top center")),
                new ChoiceSetting.Choice(2, source("Top right")),
                new ChoiceSetting.Choice(3, source("Center left")),
                new ChoiceSetting.Choice(4, source("Center")),
                new ChoiceSetting.Choice(5, source("Center right")),
                new ChoiceSetting.Choice(6, source("Bottom left")),
                new ChoiceSetting.Choice(7, source("Bottom center")),
                new ChoiceSetting.Choice(8, source("Bottom right")));
        IntegerSetting autoAnchor = new IntegerSetting(prefix + "auto_anchor", source("Anchor"),
                source("Stores the anchor chosen by Auto."), defaultAnchor.id(), 0, 8);
        IntegerSetting offsetX = new IntegerSetting(prefix + "offset_x",
                source("Horizontal offset"), source("Offset from the anchor, in GUI pixels."),
                defaultOffsetX, -32768, 32767);
        IntegerSetting offsetY = new IntegerSetting(prefix + "offset_y", source("Vertical offset"),
                source("Offset from the anchor, in GUI pixels."), defaultOffsetY, -32768, 32767);
        IntegerSetting scale = new IntegerSetting(prefix + "scale", source("Scale"),
                source("HUD size, in percent."), 100, 25, 300);
        int firstOption = options.size();
        register(textShadow, ConfigOption.Kind.BOOLEAN);
        register(useMonospaceFont, ConfigOption.Kind.BOOLEAN);
        register(translateText, ConfigOption.Kind.BOOLEAN);
        register(anchor, ConfigOption.Kind.CHOICE);
        int lastOption = options.size();
        ConfigGroup group =
                new ConfigGroup(id + ".hud", groupName == null ? source("HUD") : groupName);
        ConfigGroup parent = currentSubcategory;
        hudGroups.add(() -> {
            boolean topLevel = grouping == HudGroup.SUBCATEGORY
                    || grouping == HudGroup.AUTO && hasSubcategories;
            for (int index = firstOption; index < lastOption; index++) {
                options.get(index).group(topLevel ? group : parent, topLevel ? null : group);
            }
        });
        register(autoAnchor, null);
        register(offsetX, null);
        register(offsetY, null);
        register(scale, null);
        return new HudConfig(textShadow, useMonospaceFont, translateText, anchor, autoAnchor,
                offsetX, offsetY, scale);
    }

    void completeHudGroups() {
        // A feature can declare more subcategories after its HUD settings.
        for (Runnable group : hudGroups) {
            group.run();
        }
        hudGroups.clear();
    }

    private <T> void register(Setting<T> setting, ConfigOption.Kind optionKind) {
        if (!settingIds.add(setting.id())) {
            throw new IllegalArgumentException(
                    "Duplicate setting id " + setting.id() + " in feature " + id);
        }
        settings.add(setting);
        if (optionKind != null) {
            options.add(new ConfigOption<T>(setting, optionKind, currentSubcategory,
                    currentSubsubcategory));
        }
    }

    void localize(Languages language) {
        displayName = language.translate(originalDisplayName);
        description = language.translate(originalDescription);
        category.localize(language);
        for (Setting<?> setting : settings) {
            setting.localize(language);
        }
        for (ConfigOption<?> option : options) {
            if (option.subcategory() != null) {
                option.subcategory().localize(language);
            }
            if (option.subsubcategory() != null) {
                option.subsubcategory().localize(language);
            }
        }
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final String originalDisplayName() {
        return originalDisplayName;
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
        return enabled == null || enabled.get();
    }

    public final void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public final List<Setting<?>> settings() {
        return settingsView;
    }

    public final List<ConfigOption<?>> options() {
        return optionsView;
    }
}
