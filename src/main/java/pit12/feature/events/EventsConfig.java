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
package pit12.feature.events;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.HudAnchor;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.IntegerSetting;

public final class EventsConfig extends FeatureConfig {
    private final HudConfig hud;
    private final IntegerSetting maxEvents;
    private final BooleanSetting eventColors;

    public EventsConfig() {
        super("events", "Event List", new ConfigCategory("pit", "Pit", 75),
                "Keeps the upcoming Pit event schedule up to date from the community API.", true);
        maxEvents = integerSliderSetting("max_events", "Max Events",
                "Maximum number of upcoming events shown in the list.", 5, 1, 10, 1);
        eventColors = booleanSetting("event_colors", "Event Colors",
                "Colors each event by its in-game color.", true);
        subcategory("event_list", "Event List");
        hud = hudConfig("event_list", "Event List", HudAnchor.TOP_RIGHT, 5, 5, true, true);
    }

    public HudConfig hud() {
        return hud;
    }

    public IntegerSetting maxEvents() {
        return maxEvents;
    }

    public BooleanSetting eventColors() {
        return eventColors;
    }
}
