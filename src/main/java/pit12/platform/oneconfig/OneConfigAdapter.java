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
package pit12.platform.oneconfig;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.core.ConfigUtils;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionSubcategory;
import cc.polyfrost.oneconfig.config.elements.SubConfig;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigColorElement;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigDropdown;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigNumber;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSlider;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSwitch;
import cc.polyfrost.oneconfig.internal.config.core.ConfigCore;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.NumberSetting;
import pit12.runtime.config.Setting;
import pit12.shared.lifecycle.ClientLifecycle;

public final class OneConfigAdapter implements ClientLifecycle, ConfigChangeListener {
    private static final Logger LOGGER = Logger.getLogger(OneConfigAdapter.class.getName());
    private final ConfigCatalog catalog;
    private final List<Mod> featureMods = new ArrayList<>();
    private RootView view;

    OneConfigAdapter(ConfigCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void start() {
        catalog.clientThread().check();
        if (view != null) {
            return;
        }
        view = new RootView();
        for (FeatureConfig feature : catalog.features()) {
            View featureView = new View(feature);
            buildPage(featureView, feature);
            featureMods.add(featureView.mod);
        }
        ConfigCore.subMods.put(view.mod, new ArrayList<>(featureMods));
        ConfigCore.mods.addAll(featureMods);
        Config.register(view.mod);
        catalog.addListener(this);
    }

    @Override
    public void stop() {
        catalog.clientThread().check();
        catalog.removeListener(this);
        if (view != null) {
            ConfigCore.subMods.remove(view.mod);
            ConfigCore.mods.remove(view.mod);
            view = null;
        }
        ConfigCore.mods.removeAll(featureMods);
        featureMods.clear();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        for (Mod featureMod : featureMods) {
            View featureView = (View) featureMod.config;
            if (changes.affects(featureView.feature.id(), "enabled")) {
                featureView.enabled = featureView.feature.enabled();
            }
        }
    }

    private void buildPage(View target, FeatureConfig feature) {
        for (ConfigOption<?> option : feature.options()) {
            Setting<?> setting = option.setting();
            String category =
                    option.subcategory() == null ? "" : option.subcategory().originalDisplayName();
            String subcategory = option.subsubcategory() == null ? ""
                    : option.subsubcategory().originalDisplayName();
            OptionSubcategory section =
                    ConfigUtils.getSubCategory(target.mod.defaultPage, category, subcategory);
            BasicOption control = createOption(setting, option, category, subcategory);
            section.options.add(control);
            target.optionNames.put(setting.id(), control);
        }
    }

    // OneConfig hides SubConfig instances from the main list, so the root must remain a Config.
    private static final class RootView extends Config {
        RootView() {
            super(new Mod("12pit", ModType.HYPIXEL), "pit12-oneconfig-view/root.json", true, false);
            initialize();
        }

        @Override
        public void initialize() {
            mod.config = this;
        }

        @Override
        public void save() {}

        @Override
        public void load() {}

        @Override
        public void reInitialize() {}

        @Override
        public boolean supportsProfiles() {
            return false;
        }
    }
    public static final class View extends SubConfig {
        private final FeatureConfig feature;

        View(FeatureConfig feature) {
            super(feature.originalDisplayName(), "pit12-oneconfig-view/" + feature.id() + ".json",
                    null, feature.enabled(), feature.toggleable());
            this.feature = feature;
            initialize();
        }

        @Override
        public void initialize() {
            // Skip OneConfig's file loading because 12pit owns these settings.
            mod.config = this;
        }

        // OneConfig calls save from both card clicks and a worker. Live settings need the client thread.
        @Override
        public void save() {
            if (canToggle && Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                feature.setEnabled(enabled);
            }
        }

        @Override
        public void load() {}

        @Override
        public void reInitialize() {}

        @Override
        public boolean supportsProfiles() {
            return false;
        }
    }

    private BasicOption createOption(Setting<?> setting, ConfigOption<?> option, String category,
            String subcategory) {
        ConfigOption.Kind kind = option.kind();
        String title = setting.originalDisplayName();
        if (kind == ConfigOption.Kind.KEYBIND) {
            // OneConfig keybinds accept mouse buttons and chords, but 12pit stores one keyboard code.
            title += " (key code)";
        }
        if (setting instanceof ChoiceSetting) {
            ChoiceSetting choices = (ChoiceSetting) setting;
            List<ChoiceSetting.Choice> entries = choices.choices();
            String[] labels = new String[entries.size()];
            int[] values = new int[entries.size()];
            for (int index = 0; index < entries.size(); index++) {
                labels[index] = entries.get(index).originalDisplayName();
                values[index] = entries.get(index).value();
            }
            return dropdown(choices, labels, values, title, category, subcategory);
        }
        if (kind == ConfigOption.Kind.COLOR || kind == ConfigOption.Kind.COLOR_PICKER) {
            @SuppressWarnings("unchecked")
            Setting<Integer> color = (Setting<Integer>) setting;
            boolean rgb = kind == ConfigOption.Kind.COLOR;
            return new ConfigColorElement(null, null, title, setting.originalDescription(),
                    category, subcategory, 2, !rgb) {
                @Override
                public Object get() {
                    return new OneColor(rgb ? color.get() | 0xFF000000 : color.get());
                }

                @Override
                protected void setColor(OneColor value) {
                    setValue(color, rgb ? value.getRGB() & 0xFFFFFF : value.getRGB());
                }
            };
        }
        if (setting instanceof BooleanSetting) {
            return new ConfigSwitch(null, null, title, setting.originalDescription(), category,
                    subcategory, 2) {
                @Override
                public Object get() {
                    return setting.get();
                }

                @Override
                protected void set(Object value) {
                    setValue(setting, value);
                }
            };
        }
        NumberSetting<?> number = (NumberSetting<?>) setting;
        float minimum = (float) number.minimumValue();
        float maximum = (float) number.maximumValue();
        if (kind == ConfigOption.Kind.NUMBER) {
            int step = number.decimalPlaces() == 0 ? (int) number.stepValue() : 0;
            return new ConfigSlider(null, null, title, setting.originalDescription(), category,
                    subcategory, minimum, maximum, step, true) {
                @Override
                public Object get() {
                    return readNumber(number);
                }

                @Override
                protected void set(Object value) {
                    writeNumber(number, value);
                }
            };
        }
        return new ConfigNumber(null, null, title, setting.originalDescription(), category,
                subcategory, minimum, maximum, Math.max(1, (int) number.stepValue()), 2) {
            @Override
            public Object get() {
                return readNumber(number);
            }

            @Override
            protected void set(Object value) {
                writeNumber(number, value);
            }
        };
    }

    private BasicOption dropdown(Setting<Integer> setting, String[] labels, int[] values,
            String title, String category, String subcategory) {
        return new ConfigDropdown(null, null, title, setting.originalDescription(), category,
                subcategory, 2, labels) {
            @Override
            public Object get() {
                int value = setting.get();
                for (int index = 0; index < values.length; index++) {
                    if (values[index] == value) {
                        return index;
                    }
                }
                throw new IllegalStateException("Choice value is missing: " + setting.id());
            }

            @Override
            protected void set(Object value) {
                int index = (Integer) value;
                if (index >= 0 && index < values.length) {
                    setValue(setting, values[index]);
                }
            }
        };
    }

    private Object readNumber(NumberSetting<?> setting) {
        if (setting instanceof IntegerSetting) {
            return setting.get();
        }
        // OneConfig decimal controls require Float values.
        return setting.get().floatValue();
    }

    private void writeNumber(NumberSetting<?> setting, Object value) {
        double number = ((Number) value).doubleValue();
        // Float rounding can put OneConfig values outside 12pit's bounds.
        setValue(setting,
                Math.max(setting.minimumValue(), Math.min(setting.maximumValue(), number)));
    }

    @SuppressWarnings("unchecked")
    private void setValue(Setting<?> setting, Object value) {
        try {
            ((Setting<Object>) setting).set(value);
        } catch (IllegalArgumentException failure) {
            LOGGER.log(Level.WARNING, "Rejected OneConfig value for " + setting.id(), failure);
        }
    }
}
