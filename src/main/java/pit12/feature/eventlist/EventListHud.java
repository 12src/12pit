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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import pit12.Pit12;
import pit12.runtime.config.HudConfig;
import pit12.runtime.hud.HudElement;
import pit12.shared.rendering.UiRenderer;

final class EventListHud implements HudElement {
    private static final float FONT_SIZE = 8.0F;
    private static final int PADDING = 4;
    private static final int GAP = 8;
    private final EventListConfig config;
    private final FontRenderer vanillaFont;
    private final UiRenderer renderer;
    private EventListSnapshot snapshot;
    private EventListSnapshot content;
    private float pixelScale = Float.NaN;
    private boolean dirty = true;
    private boolean sampleLayout;
    private int iconWidth;
    private int nameX;
    private int lineHeight;
    private int width;
    private int height;

    EventListHud(EventListConfig config) {
        this.config = config;
        Minecraft minecraft = Minecraft.getMinecraft();
        vanillaFont = minecraft.fontRendererObj;
        renderer = new UiRenderer(minecraft,
                new ResourceLocation(Pit12.MOD_ID, "fonts/montserrat-regular.otf"));
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
        return config.hud();
    }

    @Override
    public void resize(float pixelScale) {
        float normalizedScale = Math.max(0.01F, pixelScale);
        if (Float.compare(this.pixelScale, normalizedScale) != 0) {
            this.pixelScale = normalizedScale;
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
        int fontHeight =
                config.useVanillaFont() ? vanillaFont.FONT_HEIGHT : renderer.fontHeight(FONT_SIZE);
        iconWidth = content.dayName.isEmpty() ? 0 : vanillaFont.getStringWidth("ℹ");
        int nameWidth = textWidth(content.dayName);
        int timeWidth = textWidth(content.dayTime);
        for (EventListSnapshot.Row row : content.rows) {
            if (config.showIcons()) {
                iconWidth = Math.max(iconWidth, vanillaFont.getStringWidth(eventIcon(row)));
            }
            nameWidth = Math.max(nameWidth, textWidth(row.type.displayName));
            timeWidth = Math.max(timeWidth, textWidth(row.time));
        }
        lineHeight = Math.max(fontHeight, iconWidth > 0 ? vanillaFont.FONT_HEIGHT : 0) + 2;
        nameX = PADDING + (iconWidth > 0 ? iconWidth + GAP : 0);
        width = Math.max(nameX + nameWidth + (timeWidth > 0 ? GAP + timeWidth : 0),
                PADDING + textWidth(content.message)) + PADDING;
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
            text(content.dayName, nameX, y, 0xFFB0B0B0);
            text(content.dayTime, width - PADDING - textWidth(content.dayTime), y, 0xFFB0B0B0);
            y += lineHeight + 2;
        }
        if (content.rows.isEmpty()) {
            text(content.message, PADDING, y, 0xFFFFFFFF);
            return;
        }
        for (EventListSnapshot.Row row : content.rows) {
            if (config.showIcons()) {
                int color = row.phase == PitEvent.Phase.ACTIVE ? 0xFF55FF55
                        : row.phase == PitEvent.Phase.PREPARING ? 0xFFFFFF55
                                : row.major ? 0xFFFFAA00 : 0xFF55FFFF;
                icon(eventIcon(row), y, color);
            }
            text(row.type.displayName, nameX, y,
                    config.showColors() ? 0xFF000000 | row.type.color : 0xFFFFFFFF);
            text(row.time, width - PADDING - textWidth(row.time), y, 0xFFFFFFFF);
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
        int x = PADDING + (iconWidth - vanillaFont.getStringWidth(symbol)) / 2;
        int iconY = y + (lineHeight - 2 - vanillaFont.FONT_HEIGHT) / 2;
        // The vanilla symbols have the intended proportions, including smaller circles.
        vanillaFont.drawString(symbol, x, iconY, color, config.hud().textShadow().get());
    }

    private int textWidth(String text) {
        return config.useVanillaFont() ? vanillaFont.getStringWidth(text)
                : renderer.textWidth(text, FONT_SIZE);
    }

    private void text(String text, int x, int y, int color) {
        boolean shadow = config.hud().textShadow().get();
        if (config.useVanillaFont()) {
            vanillaFont.drawString(text, x, y, color, shadow);
        } else {
            renderer.text(text, x, y, FONT_SIZE, color, shadow);
        }
    }
}
