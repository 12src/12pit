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
package pit12.runtime.config.profile.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

final class LegacyProfileSettings {
    private final Map<String, Map<String, LegacyKey>> rules = new HashMap<>();

    static LegacyProfileSettings builtIn() {
        LegacyProfileSettings settings = new LegacyProfileSettings();
        // 0e1516ccc02f731aa34f979638dd15439a64b193
        settings.registerAlias("auto_denick", "enabled", "denick", "automatic");
        settings.registerSameKeys("auto_denick", "denick", "success_only", "monitor");
        settings.registerSameKeys("telebow_prediction", "telebow", "prediction_margin_ms",
                "teleport_extra_margin_ms", "cooldown_detection", "correct_cooldown");
        settings.registerSameKeys("timer_tracking", "debufftimers", "pin_hit_window_ms",
                "venom_hit_window_ms", "venom_reach_blocks", "venom_hitbox_margin_blocks",
                "jump_boost_sound_required", "jump_boost_reset_on_low_jump", "jump_boost_grace_ms");
        return settings;
    }

    JsonElement find(JsonObject features, String featureId, String settingId) {
        Map<String, LegacyKey> aliases = rules.get(featureId);
        if (aliases == null) {
            return null;
        }
        LegacyKey oldKey = aliases.get(settingId);
        if (oldKey == null) {
            return null;
        }
        JsonElement oldFeature = features.get(oldKey.featureId);
        return oldFeature != null && oldFeature.isJsonObject()
                ? oldFeature.getAsJsonObject().get(oldKey.settingId)
                : null;
    }

    void registerSameKeys(String featureId, String oldFeatureId, String... settingIds) {
        for (String settingId : settingIds) {
            registerAlias(featureId, settingId, oldFeatureId, settingId);
        }
    }

    void registerAlias(String featureId, String settingId, String oldFeatureId,
            String oldSettingId) {
        rules.computeIfAbsent(featureId, id -> new HashMap<>()).put(settingId,
                new LegacyKey(oldFeatureId, oldSettingId));
    }

    private static final class LegacyKey {
        private final String featureId;
        private final String settingId;

        private LegacyKey(String featureId, String settingId) {
            this.featureId = featureId;
            this.settingId = settingId;
        }
    }
}
