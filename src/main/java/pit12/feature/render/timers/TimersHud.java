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
package pit12.feature.timers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudGroupConfig;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRenderer;
import pit12.shared.rendering.FontWidths;
import pit12.shared.text.TextCodes;

final class TimersHud implements HudElement {
    private final Minecraft minecraft;
    private final TimersConfig config;
    private final HudRenderer renderer;
    private final HudConfig hudConfig;
    // A null type uses the combined layout.
    private final TimerType type;
    private final String id;
    final List<Row> rows = new ArrayList<>();
    private List<Row> content = Collections.emptyList();
    private float pixelScale = Float.NaN;
    private boolean dirty = true;
    private boolean editing;
    private boolean unicode;
    private int width;
    private int height;
    private int lineHeight;
    private boolean metricsDirty = true;
    private final int[] titleWidths = new int[TimerType.values().length];
    float visibleHeight = Float.POSITIVE_INFINITY;

    TimersHud(Minecraft minecraft, TimersConfig config, HudRenderer renderer, TimerType type) {
        this.minecraft = minecraft;
        this.config = config;
        this.renderer = renderer;
        this.type = type;
        id = type == null ? "debuff-timers" : "debuff-timers." + type.id;
        hudConfig = type == null ? config.hud : config.hudGroups.get(type).hud();
    }

    void invalidate() {
        dirty = true;
    }

    boolean active() {
        if (type != null) {
            return config.hudGroups.get(type).independent();
        }
        for (HudGroupConfig layout : config.hudGroups.values()) {
            if (!layout.independent()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return type == null ? config.displayName()
                : config.displayName() + ": " + TextCodes.strip(type.title);
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
        if (Float.compare(this.pixelScale, pixelScale) != 0) {
            this.pixelScale = pixelScale;
            renderer.resize(pixelScale);
            dirty = true;
            metricsDirty = true;
        }
    }

    @Override
    public void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
        FontRenderer font = minecraft.fontRendererObj;
        boolean nextUnicode = font.getUnicodeFlag();
        if (!dirty && this.editing == editing && unicode == nextUnicode)
            return;
        dirty = false;
        this.editing = editing;
        metricsDirty |= unicode != nextUnicode;
        unicode = nextUnicode;
        if (editing) {
            content = new ArrayList<>();
            for (TimerType next : TimerType.values()) {
                if (type == null ? !config.hudGroups.get(next).independent() : type == next) {
                    content.add(sampleRow(next));
                }
            }
        } else {
            content = rows;
        }
        lineHeight = renderer.fontHeight() + 2;
        if (metricsDirty) {
            Arrays.fill(titleWidths, -1);
            if (editing) {
                for (Row row : rows) {
                    row.nameWidth = -1;
                    row.timeWidth = -1;
                }
            }
        }
        int nameWidth = 0;
        int timeWidth = 0;
        int titleWidth = 0;
        int lines = content.size();
        TimerType previousType = null;
        for (Row row : content) {
            if (metricsDirty) {
                row.nameWidth = -1;
                row.timeWidth = -1;
            }
            if (previousType != null && previousType != row.type)
                lines++;
            if (previousType != row.type && config.showGroupNames.get()) {
                lines++;
                int index = row.type.ordinal();
                if (titleWidths[index] < 0)
                    titleWidths[index] = FontWidths.measure(font, row.type.title);
                titleWidth = Math.max(titleWidth, titleWidths[index]);
            }
            previousType = row.type;
            if (row.nameWidth < 0)
                row.nameWidth = FontWidths.measure(font, row.name);
            nameWidth = Math.max(nameWidth, row.nameWidth);
            if (row.timeWidth < 0)
                row.timeWidth = FontWidths.measure(font, row.time);
            timeWidth = Math.max(timeWidth, row.timeWidth);
        }
        width = content.isEmpty() ? 0 : 8 + Math.max(titleWidth, nameWidth + 10 + timeWidth);
        height = content.isEmpty() ? 0 : 8 + lineHeight * lines;
        metricsDirty = false;
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
        renderer.resize(pixelScale);
        int y = 4;
        TimerType previousType = null;
        for (Row row : content) {
            if (previousType != null && previousType != row.type)
                y += lineHeight;
            if (!editing && y >= visibleHeight + lineHeight)
                break;
            if (previousType != row.type && config.showGroupNames.get()) {
                renderer.text(row.type.title, 4, y, 0xFFFFFFFF, hudConfig);
                y += lineHeight;
            }
            previousType = row.type;
            renderer.text(row.name, 4, y, 0xFFFFFFFF, hudConfig);
            renderer.text(row.time, width - 4 - row.timeWidth, y, row.group.color(config),
                    hudConfig);
            y += lineHeight;
        }
    }

    private static Row sampleRow(TimerType type) {
        switch (type) {
            case PIN:
                return new Row(type, Group.OWN, "§5[§b§l120§5] §7PlayerOne", 3_500_000_000L);
            case VENOM:
                return new Row(type, Group.FRIEND, "§e[§f§l117§e] §aPlayerThree", 6_000_000_000L);
            case JUMP_BOOST:
                return new Row(type, Group.ENEMY, "§c[§d§l109§c] §7PlayerFour", 8_000_000_000L);
            case TELEBOW:
                return new Row(type, Group.OTHER, "§6[§f§l112§6] §bPlayerSeven", 15_000_000_000L);
            default:
                throw new AssertionError(type);
        }
    }

    enum TimerType {
        PIN("pin", "§e§lPin"),
        VENOM("venom", "§d§lVenom"),
        JUMP_BOOST("jump_boost", "§a§lJump Boost"),
        TELEBOW("telebow", "§b§lTelebow");

        final String id;
        final String title;
        final String color;

        TimerType(String id, String title) {
            this.id = id;
            this.title = title;
            color = title.substring(0, 2);
        }
    }
    enum Group {
        OWN, FRIEND, ENEMY, OTHER;

        int color(TimersConfig config) {
            switch (this) {
                case OWN:
                    return config.ownColor.get();
                case FRIEND:
                    return config.friendColor.get();
                case ENEMY:
                    return config.enemyColor.get();
                default:
                    return config.otherColor.get();
            }
        }
    }
    static final class Row {
        final TimerType type;
        Group group;
        String name;
        String time;
        long until;
        private long tenths = Long.MIN_VALUE;
        int nameWidth = -1;
        int timeWidth = -1;

        Row(TimerType type, Group group, String name, long remainingNanos) {
            this.type = type;
            update(group, name, remainingNanos);
        }

        void update(Group group, String name, long remainingNanos) {
            this.group = group;
            if (!name.equals(this.name)) {
                this.name = name;
                nameWidth = -1;
            }
            updateTime(remainingNanos);
        }

        boolean updateTime(long remainingNanos) {
            long next = (remainingNanos + 99_999_999L) / 100_000_000L;
            if (tenths == next)
                return false;
            tenths = next;
            time = tenths / 10 + "." + tenths % 10 + "s";
            timeWidth = -1;
            return true;
        }
    }
}
