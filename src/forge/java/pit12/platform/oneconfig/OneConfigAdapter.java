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
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionSubcategory;
import cc.polyfrost.oneconfig.config.elements.SubConfig;
import cc.polyfrost.oneconfig.events.EventManager;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigColorElement;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigDropdown;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigKeyBind;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSlider;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSwitch;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigTextBox;
import cc.polyfrost.oneconfig.internal.config.core.ConfigCore;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.KeybindSetting;
import pit12.runtime.config.NumberSetting;
import pit12.runtime.config.Setting;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class OneConfigAdapter implements ClientLifecycle, ConfigChangeListener {
    private static final Logger LOGGER = Logger.getLogger(OneConfigAdapter.class.getName());
    private final ConfigCatalog catalog;
    private final List<Mod> featureMods = new ArrayList<>();
    private final List<ConfigKeyBind> keybindControls = new ArrayList<>();
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
        for (ConfigKeyBind control : keybindControls) {
            EventManager.INSTANCE.unregister(control);
        }
        keybindControls.clear();
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

    // OneConfig hides SubConfig from the main list. This one needs to stay visible.
    public static final class RootView extends Config {
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
        public final FeatureConfig feature;

        View(FeatureConfig feature) {
            super(feature.originalDisplayName(), "pit12-oneconfig-view/" + feature.id() + ".json",
                    null, feature.enabled(), feature.toggleable());
            this.feature = feature;
            initialize();
        }

        @Override
        public void initialize() {
            // We already load these settings ourselves.
            mod.config = this;
        }

        // OneConfig also calls save from a worker, so don't touch live settings off the client thread.
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
        if (kind == ConfigOption.Kind.TEXT) {
            return new ConfigTextBox(null, null, title, setting.originalDescription(), category,
                    subcategory, 2, "", false, false) {
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
        if (kind == ConfigOption.Kind.KEYBIND) {
            KeybindSetting keySetting = (KeybindSetting) setting;
            KeybindValue binding = new KeybindValue();
            try {
                // ConfigKeyBind reads this field before calling our getter.
                ConfigKeyBind control =
                        new ConfigKeyBind(KeybindValue.class.getDeclaredField("value"), binding,
                                title, setting.originalDescription(), category, subcategory, 2) {
                            @Override
                            public Object get() {
                                int key = keySetting.get();
                                List<Integer> keys = binding.value.getKeyBinds();
                                if (key != keybindValue(keys)) {
                                    binding.value.clearKeys();
                                    if (Keybind.modifier(key) != 0)
                                        binding.value.addKey(Keybind.modifier(key));
                                    if (Keybind.key(key) != 0)
                                        binding.value.addKey(Keybind.key(key));
                                }
                                return binding.value;
                            }

                            @Override
                            protected void set(Object value) {
                                List<Integer> keys = ((OneKeyBind) value).getKeyBinds();
                                setValue(keySetting, keybindValue(keys));
                            }
                        };
                keybindControls.add(control);
                return control;
            } catch (NoSuchFieldException failure) {
                throw new IllegalStateException("OneConfig keybind field is missing", failure);
            }
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

    private static int keybindValue(List<Integer> keys) {
        if (keys.isEmpty())
            return 0;
        if (keys.size() == 1)
            return keys.get(0);
        return Keybind.isModifier(keys.get(0)) ? Keybind.encode(keys.get(1), keys.get(0))
                : Keybind.encode(keys.get(0), keys.get(1));
    }

    private static final class KeybindValue {
        private final OneKeyBind value = new OneKeyBind() {
            @Override
            public void addKey(int key, boolean mouse) {
                if (mouse || key <= 0 || key >= Keyboard.KEYBOARD_SIZE
                        || Keyboard.getKeyName(key) == null) {
                    return;
                }
                if (getSize() == 0 || getSize() == 1
                        && (Keybind.isModifier(key) || Keybind.isModifier(getKeyBinds().get(0))))
                    super.addKey(key, false);
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
        // OneConfig wants a Float here.
        return setting.get().floatValue();
    }

    private void writeNumber(NumberSetting<?> setting, Object value) {
        double number = ((Number) value).doubleValue();
        // Float rounding can push a value past the setting's bounds.
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
