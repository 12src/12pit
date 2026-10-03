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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import pit12.runtime.config.HudConfig;
import pit12.runtime.hud.HudElement;

final class EventListHud implements HudElement {
    private static final int EDGE_PADDING = 5;
    private static final int LINE_SPACING = 2;
    private static final int COLUMN_GAP = 6;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final int TIME_COLOR = 0xFFB0B0B0;
    // Status icons use vanilla formatting codes for their state colors.
    private static final String ICON_ACTIVE = "§a●";
    private static final String ICON_WARNING = "§e⚠";
    private static final String ICON_EXPIRED = "§c●";
    private static final String ICON_MAJOR = "§6✦";
    private static final String ICON_MINOR = "§b○";
    private static final List<PitEvent> SAMPLE = sampleEvents();
    private final EventsConfig config;
    private final EventsFeature feature;
    private final HudConfig hudConfig;
    private final FontRenderer font;
    private List<PitEvent> lines = Collections.emptyList();
    private int lineHeight;
    private int iconWidth;
    private int nameWidth;
    private int timeWidth;
    private int width;
    private int height;

    EventListHud(EventsConfig config, EventsFeature feature) {
        this.config = config;
        this.feature = feature;
        hudConfig = config.hud();
        font = Minecraft.getMinecraft().fontRendererObj;
        lineHeight = font.FONT_HEIGHT + LINE_SPACING;
    }

    @Override
    public String id() {
        return "event-list";
    }

    @Override
    public String displayName() {
        return "Event List";
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public HudConfig config() {
        return hudConfig;
    }

    @Override
    public void resize(float pixelScale) {
        lineHeight = font.FONT_HEIGHT + LINE_SPACING;
    }

    @Override
    public void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
        List<PitEvent> content = feature.events();
        if (editing && content.isEmpty()) {
            content = SAMPLE;
        }
        lines = displayEvents(content);
        iconWidth = 0;
        nameWidth = 0;
        timeWidth = 0;
        for (PitEvent event : lines) {
            iconWidth = Math.max(iconWidth, textWidth(icon(event)));
            nameWidth = Math.max(nameWidth, textWidth(event.name()));
            timeWidth = Math.max(timeWidth, textWidth(event.formattedTimeUntil()));
        }
        width = Math.max(1, EDGE_PADDING + iconWidth + (lines.isEmpty() ? 0 : COLUMN_GAP)
                + nameWidth + (lines.isEmpty() ? 0 : COLUMN_GAP + timeWidth) + EDGE_PADDING);
        height = Math.max(lineHeight, EDGE_PADDING + lines.size() * lineHeight);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        boolean shadow = hudConfig.textShadow().get().booleanValue();
        int y = EDGE_PADDING;
        int nameX = EDGE_PADDING + iconWidth + COLUMN_GAP;
        int timeRight = EDGE_PADDING + iconWidth + COLUMN_GAP + nameWidth + COLUMN_GAP + timeWidth;
        for (PitEvent event : lines) {
            text(icon(event), EDGE_PADDING, y, 0xFFFFFF, true);
            int color = config.eventColors().get().booleanValue() ? 0xFF000000 | event.color()
                    : DEFAULT_COLOR;
            text(event.name(), nameX, y, color, shadow);
            String time = event.formattedTimeUntil();
            text(time, timeRight - textWidth(time), y, TIME_COLOR, shadow);
            y += lineHeight;
        }
    }

    // At most one expired event (the most recent) plus upcoming events in chronological order.
    private List<PitEvent> displayEvents(List<PitEvent> snapshot) {
        int maximum = config.maxEvents().get().intValue();
        PitEvent lastExpired = null;
        List<PitEvent> upcoming = new ArrayList<PitEvent>(snapshot.size());
        for (PitEvent event : snapshot) {
            if (event.secondsUntil() < 0) {
                if (lastExpired == null || event.timestamp() > lastExpired.timestamp()) {
                    lastExpired = event;
                }
            } else {
                upcoming.add(event);
            }
        }
        List<PitEvent> result = new ArrayList<PitEvent>(maximum);
        if (lastExpired != null) {
            result.add(lastExpired);
        }
        for (PitEvent event : upcoming) {
            if (result.size() >= maximum) {
                break;
            }
            result.add(event);
        }
        return result;
    }

    private static String icon(PitEvent event) {
        if (event.isActive()) {
            return ICON_ACTIVE;
        }
        if (event.isInWarningPeriod()) {
            return ICON_WARNING;
        }
        if (event.isExpired()) {
            return ICON_EXPIRED;
        }
        return event.isMajor() ? ICON_MAJOR : ICON_MINOR;
    }

    private void text(String text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            font.drawStringWithShadow(text, x, y, color);
        } else {
            font.drawString(text, x, y, color, false);
        }
    }

    private int textWidth(String text) {
        return font.getStringWidth(text);
    }

    private static List<PitEvent> sampleEvents() {
        long now = System.currentTimeMillis();
        List<PitEvent> sample = new ArrayList<PitEvent>();
        sample.add(new PitEvent("Robbery", now - 60 * 1000, "major"));
        sample.add(new PitEvent("KOTH", now + 45 * 1000, "minor"));
        sample.add(new PitEvent("2x Rewards", now + 1500 * 1000, "minor"));
        sample.add(new PitEvent("Beast", now + 4320 * 1000, "major"));
        return Collections.unmodifiableList(sample);
    }
}
