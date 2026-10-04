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

import java.util.Collections;
import pit12.runtime.config.HudConfig;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRenderer;

final class EventListHud implements HudElement {
    private static final int PADDING = 4;
    private static final int GAP = 8;
    private final EventListConfig config;
    private final HudConfig hudConfig;
    private final HudRenderer renderer;
    private EventListSnapshot snapshot;
    private EventListSnapshot content;
    private float pixelScale = Float.NaN;
    private boolean monospaceFont;
    private boolean dirty = true;
    private boolean sampleLayout;
    private int iconWidth;
    private int nameX;
    private int lineHeight;
    private int width;
    private int height;

    EventListHud(EventListConfig config, HudRenderer renderer) {
        this.config = config;
        hudConfig = config.hud();
        this.renderer = renderer;
        snapshot = EventListSnapshot.build(Collections.emptyList(), config,
                System.currentTimeMillis(), "", true);
    }

    void snapshot(EventListSnapshot snapshot) {
        this.snapshot = snapshot;
        dirty = true;
    }

    void close() {
        renderer.close();
        pixelScale = Float.NaN;
        dirty = true;
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
        float normalizedScale = Math.max(0.01F, pixelScale);
        boolean monospaceFont = hudConfig.useMonospaceFont().get();
        if (Float.compare(this.pixelScale, normalizedScale) != 0
                || this.monospaceFont != monospaceFont) {
            this.pixelScale = normalizedScale;
            this.monospaceFont = monospaceFont;
            renderer.resize(normalizedScale);
            dirty = true;
        }
    }

    @Override
    public void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
        boolean sample = editing && (!enabled() || snapshot.rows.isEmpty());
        if (!dirty && sample == sampleLayout) {
            return;
        }
        content = sample ? EventListSnapshot.sample(config) : snapshot;
        sampleLayout = sample;
        dirty = false;
        int fontHeight = Math.max(renderer.fontHeight(content.dayName, hudConfig),
                renderer.fontHeight(content.dayTime, hudConfig));
        iconWidth = content.dayName.isEmpty() ? 0 : renderer.textWidth("ℹ", false);
        if (!content.dayName.isEmpty()) {
            fontHeight = Math.max(fontHeight, renderer.fontHeight("ℹ", false));
        }
        int nameWidth = renderer.textWidth(content.dayName, hudConfig);
        int timeWidth = renderer.textWidth(content.dayTime, hudConfig);
        for (EventListSnapshot.Row row : content.rows) {
            if (config.showIcons()) {
                String icon = eventIcon(row);
                iconWidth = Math.max(iconWidth, renderer.textWidth(icon, false));
                fontHeight = Math.max(fontHeight, renderer.fontHeight(icon, false));
            }
            nameWidth = Math.max(nameWidth, renderer.textWidth(row.type.displayName, hudConfig));
            timeWidth = Math.max(timeWidth, renderer.textWidth(row.time, hudConfig));
            fontHeight = Math.max(fontHeight, renderer.fontHeight(row.type.displayName, hudConfig));
            fontHeight = Math.max(fontHeight, renderer.fontHeight(row.time, hudConfig));
        }
        lineHeight = Math.max(fontHeight, renderer.fontHeight(content.message, hudConfig)) + 2;
        nameX = PADDING + (iconWidth > 0 ? iconWidth + GAP : 0);
        width = Math.max(nameX + nameWidth + (timeWidth > 0 ? GAP + timeWidth : 0),
                PADDING + renderer.textWidth(content.message, hudConfig)) + PADDING;
        height = PADDING * 2 + Math.max(1, content.rows.size()) * lineHeight;
        if (!content.dayName.isEmpty()) {
            height += lineHeight + 2;
        }
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
        int y = PADDING;
        if (!content.dayName.isEmpty()) {
            icon("ℹ", y, 0xFF808080);
            renderer.text(content.dayName, nameX, y, 0xFFB0B0B0, hudConfig);
            renderer.text(content.dayTime,
                    width - PADDING - renderer.textWidth(content.dayTime, hudConfig), y, 0xFFB0B0B0,
                    hudConfig);
            y += lineHeight + 2;
        }
        if (content.rows.isEmpty()) {
            renderer.text(content.message, PADDING, y, 0xFFFFFFFF, hudConfig);
            return;
        }
        for (EventListSnapshot.Row row : content.rows) {
            if (config.showIcons()) {
                int color = row.phase == PitEvent.Phase.ACTIVE ? 0xFF55FF55
                        : row.phase == PitEvent.Phase.PREPARING ? 0xFFFFFF55
                                : row.major ? 0xFFFFAA00 : 0xFF55FFFF;
                icon(eventIcon(row), y, color);
            }
            renderer.text(row.type.displayName, nameX, y,
                    config.showColors() ? 0xFF000000 | row.type.color : 0xFFFFFFFF, hudConfig);
            renderer.text(row.time, width - PADDING - renderer.textWidth(row.time, hudConfig), y,
                    0xFFFFFFFF, hudConfig);
            y += lineHeight;
        }
    }

    private String eventIcon(EventListSnapshot.Row row) {
        if (row.phase == PitEvent.Phase.ACTIVE) {
            return "●";
        }
        if (row.phase == PitEvent.Phase.PREPARING) {
            return "⚠";
        }
        return row.major ? "✦" : "○";
    }

    private void icon(String symbol, int y, int color) {
        int x = PADDING + (iconWidth - renderer.textWidth(symbol, false)) / 2;
        int iconY = y + (lineHeight - 2 - renderer.fontHeight(symbol, false)) / 2;
        // The vanilla symbols have the intended proportions, including smaller circles.
        renderer.text(symbol, x, iconY, color, hudConfig.textShadow().get(), false);
    }
}
