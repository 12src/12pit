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
package pit12.feature.client.webui;

import static pit12.feature.client.webui.WebUiJson.object;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pit12.feature.client.profile.api.ProfileSummary;
import pit12.feature.client.profile.api.Profiles;
import pit12.feature.client.profile.api.ProfilesSnapshot;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationEntry;
import pit12.feature.client.relation.api.Relations;
import pit12.feature.combat.swap.api.SwapBindings;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.NumberSetting;
import pit12.runtime.config.Setting;
import pit12.runtime.languages.Languages;
import pit12.shared.build.BuildConfig;
import pit12.shared.input.Keybind;

final class WebUiState {
    private static final String BUILD_LABEL = buildLabel();
    private final ConfigCatalog catalog;
    private final WebUiConfig config;
    private final Profiles profiles;
    private final Relations relations;
    private final SwapBindings swapBindings;
    private final Languages language;
    private final WebUiPreferences preferences;

    WebUiState(ConfigCatalog catalog, WebUiConfig config, Profiles profiles, Relations relations,
            SwapBindings swapBindings, Languages language, WebUiPreferences preferences) {
        this.catalog = catalog;
        this.config = config;
        this.profiles = profiles;
        this.relations = relations;
        this.swapBindings = swapBindings;
        this.language = language;
        this.preferences = preferences;
    }

    Map<String, Object> catalog() {
        List<Object> features = new ArrayList<>();
        for (FeatureConfig feature : catalog.features())
            features.add(featureState(feature));
        return object("version", BUILD_LABEL, "language", language.locale(), "features", features,
                "settings", featureState(config));
    }

    List<Object> values() {
        List<Object> values = new ArrayList<>();
        for (FeatureConfig feature : catalog.features())
            values.addAll(values(feature));
        return values;
    }

    List<Object> values(FeatureConfig feature) {
        List<Object> values = new ArrayList<>();
        if (feature.toggleable())
            values.add(object("featureId", feature.id(), "settingId", "enabled", "value",
                    feature.enabled()));
        for (ConfigOption<?> option : feature.options()) {
            Setting<?> setting = option.setting();
            Map<String, Object> value = object("featureId", feature.id(), "settingId", setting.id(),
                    "value", setting.get());
            if (option.kind() == ConfigOption.Kind.KEYBIND)
                value.put("keyName", Keybind.name((Integer) setting.get()));
            values.add(value);
        }
        return values;
    }

    Map<String, Object> preferences() {
        return object("favorites", preferences.favorites(), "languageHint",
                config.languageHint().get(), "ready", preferences.ready(), "problem",
                Languages.sourceText(preferences.problem()));
    }

    Map<String, Object> profiles() {
        ProfilesSnapshot snapshot = profiles.snapshot();
        List<Object> entries = new ArrayList<>();
        for (ProfileSummary profile : snapshot.profiles())
            entries.add(object("id", profile.id().toString(), "name", profile.name()));
        return object("loadState", snapshot.loadState().name(), "activeId",
                snapshot.activeProfileId() == null ? null : snapshot.activeProfileId().toString(),
                "entries", entries, "problems", snapshot.problems(), "unpersisted",
                snapshot.hasUnpersistedChanges());
    }

    Map<String, Object> relations() {
        List<Object> entries = new ArrayList<>();
        for (Relation type : new Relation[] {Relation.FRIEND, Relation.ENEMY}) {
            for (RelationEntry entry : relations.entries(type))
                entries.add(object("uuid",
                        entry.playerId() == null ? null : entry.playerId().toString(), "name",
                        entry.name(), "relation", type.name()));
        }
        return object("problem", relations.readinessProblem(), "entries", entries);
    }

    Map<String, Object> transfer() {
        return object("swapBindings", object("problem", swapBindings.readinessProblem()));
    }

    List<Object> links() {
        List<Object> links = new ArrayList<>();
        links.add(
                object("id", "discord", "name", "Discord", "url", "https://discord.gg/e9PRKMUenc"));
        links.add(
                object("id", "github", "name", "GitHub", "url", "https://github.com/12src/12pit"));
        return links;
    }

    private Map<String, Object> featureState(FeatureConfig feature) {
        List<Object> sections = new ArrayList<>();
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (ConfigOption<?> option : feature.options()) {
            String sectionId =
                    option.subsubcategory() == null ? null : option.subsubcategory().id();
            String groupId = (option.subcategory() == null ? "" : option.subcategory().id()) + "/"
                    + (sectionId == null ? "" : sectionId);
            Map<String, Object> section = byId.get(groupId);
            if (section == null) {
                section = object("id", sectionId, "name",
                        option.subsubcategory() == null ? null
                                : option.subsubcategory().displayName(),
                        "subcategory",
                        option.subcategory() == null ? null
                                : object("id", option.subcategory().id(), "name",
                                        option.subcategory().displayName()),
                        "options", new ArrayList<>());
                byId.put(groupId, section);
                sections.add(section);
            }
            @SuppressWarnings("unchecked")
            List<Object> options = (List<Object>) section.get("options");
            options.add(optionState(option));
        }
        ConfigCategory category = feature.category();
        return object("id", feature.id(), "name", feature.displayName(), "description",
                feature.description(), "categoryId", category == null ? "" : category.id(),
                "category", category == null ? "" : category.displayName(), "toggleable",
                feature.toggleable(), "enabled", feature.enabled(), "sections", sections);
    }

    private static String buildLabel() {
        if (BuildConfig.RELEASE_BUILD) {
            return BuildConfig.VERSION;
        }
        if (!BuildConfig.GIT_COMMIT.isEmpty()) {
            return BuildConfig.GIT_COMMIT.substring(0,
                    Math.min(7, BuildConfig.GIT_COMMIT.length()));
        }
        return "dev";
    }

    private Map<String, Object> optionState(ConfigOption<?> option) {
        Setting<?> setting = option.setting();
        Map<String, Object> value = object("id", setting.id(), "name", setting.displayName(),
                "description", setting.description(), "kind",
                setting == config.language() ? "CHOICE" : option.kind().name(), "value",
                setting.get());
        if (setting instanceof NumberSetting<?>) {
            NumberSetting<?> number = (NumberSetting<?>) setting;
            value.put("min", number.minimumValue());
            value.put("max", number.maximumValue());
            value.put("step", number.stepValue());
        }
        if (setting == config.language()) {
            List<Object> choices = new ArrayList<>();
            for (Map.Entry<String, String> entry : language.names().entrySet()) {
                choices.add(object("value", entry.getKey(), "name", entry.getValue(), "locale",
                        entry.getKey(), "displayName", language.displayNameSource(entry.getKey())));
            }
            value.put("choices", choices);
        } else if (setting instanceof ChoiceSetting) {
            List<Object> choices = new ArrayList<>();
            for (ChoiceSetting.Choice choice : ((ChoiceSetting) setting).choices()) {
                choices.add(object("value", choice.value(), "name", choice.displayName()));
            }
            value.put("choices", choices);
        }
        if (option.kind() == ConfigOption.Kind.KEYBIND) {
            value.put("keyName", Keybind.name((Integer) setting.get()));
        }
        return value;
    }
}
