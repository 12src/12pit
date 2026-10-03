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
package pit12.feature.eventlist;

import java.util.EnumMap;
import java.util.Map;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.HudAnchor;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.IntegerSetting;

public final class EventListConfig extends FeatureConfig {
    private final HudConfig hud;
    private final BooleanSetting showDayNight;
    private final BooleanSetting showIcons;
    private final BooleanSetting showColors;
    private final BooleanSetting zeroPadding;
    private final IntegerSetting eventCount;
    private final ChoiceSetting timeFormat;
    private final BooleanSetting showMajorEvents;
    private final BooleanSetting showMinorEvents;
    private final Map<EventType, BooleanSetting> shownEvents = new EnumMap<>(EventType.class);

    public EventListConfig() {
        super("eventlist", "Event List", new ConfigCategory("render", "Render", 100),
                "Shows upcoming Pit events and their current stages.");
        subcategory("display", "Display");
        hud = hudConfig("event_list", "Event List", HudAnchor.TOP_RIGHT, -6, 6, true);
        eventCount = integerSliderSetting("event_count", "Event count",
                "Sets the number of event rows. The day and night row is separate.", 6, 1, 20, 1);
        showDayNight = booleanSetting("show_day_night", "Show day and night",
                "Shows the Pit day and night cycle above the events.", true);
        showIcons = booleanSetting("show_icons", "Show status icons",
                "Marks upcoming major and minor events, preparation, and active events.", true);
        showColors = booleanSetting("show_colors", "Show event colors",
                "Uses each event's color for its name.", true);
        zeroPadding = booleanSetting("zero_padding", "Pad time with zeros",
                "Shows two digits for each part of a countdown.", true);
        timeFormat = choiceSetting("time_format", "Time format", "Sets how event times are shown.",
                0, new ChoiceSetting.Choice(0, "Countdown"),
                new ChoiceSetting.Choice(1, "Local time (24-hour)"),
                new ChoiceSetting.Choice(2, "Countdown and local time"));
        subcategory("major_events", "Major events");
        showMajorEvents = booleanSetting("show_major_events", "Show major events",
                "Shows major events in the list.", true);
        for (EventType type : EventType.values()) {
            if (type.major) {
                shownEvents.put(type, booleanSetting("show_" + type.id, type.displayName,
                        "Shows " + type.displayName + " in the list.", true));
            }
        }
        subcategory("minor_events", "Minor events");
        showMinorEvents = booleanSetting("show_minor_events", "Show minor events",
                "Shows minor events in the list.", true);
        for (EventType type : EventType.values()) {
            if (!type.major) {
                shownEvents.put(type, booleanSetting("show_" + type.id, type.displayName,
                        "Shows " + type.displayName + " in the list.", true));
            }
        }
    }

    HudConfig hud() {
        return hud;
    }

    boolean showDayNight() {
        return showDayNight.get();
    }

    boolean showIcons() {
        return showIcons.get();
    }

    boolean showColors() {
        return showColors.get();
    }

    boolean zeroPadding() {
        return zeroPadding.get();
    }

    int eventCount() {
        return eventCount.get();
    }

    int timeFormat() {
        return timeFormat.get();
    }

    boolean shows(PitEvent event) {
        return shownEvents.get(event.type).get()
                && (event.major ? showMajorEvents.get() : showMinorEvents.get());
    }
}
