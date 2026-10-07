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
package pit12.feature.hudeditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import pit12.feature.profile.api.Profiles;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.HudAnchor;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudPlacement;
import pit12.runtime.hud.HudBounds;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRegistry;
import pit12.runtime.hud.HudRenderer;
import pit12.shared.rendering.UiRenderState;

final class HudEditorController {
    private enum Gesture {
        NONE, MOVE, MARQUEE, NUDGE
    }

    private final HudRegistry registry;
    private final ConfigCatalog configs;
    private final Profiles profiles;
    private final HudRenderer renderer = new HudRenderer();
    private final UiRenderState renderState = new UiRenderState();
    private final HudEditorHistory history;
    private final HudEditorSnap snap = new HudEditorSnap();
    private final Set<HudElement> selected = new LinkedHashSet<>();
    private final Map<HudElement, HudBounds> layouts = new LinkedHashMap<>();
    private final Map<HudElement, HudBounds> origins = new LinkedHashMap<>();
    private boolean applying;
    private final ConfigChangeListener configListener = changes -> {
        if (!applying) {
            invalidate();
        }
    };
    private final Runnable profileListener = this::profileChanged;
    private HudElement primary;
    private UUID profileId;
    private Gesture gesture = Gesture.NONE;
    private HudEditorSnap.Box group;
    private int screenWidth;
    private int screenHeight;
    private float pixelScale = 1.0F;
    private int pressX;
    private int pressY;
    private int pointerX;
    private int pointerY;
    private int deltaX;
    private int deltaY;
    private boolean moved;
    private boolean additive;
    private boolean opened;
    private boolean live;

    HudEditorController(HudRegistry registry, ConfigCatalog configs, Profiles profiles) {
        this.registry = registry;
        this.configs = configs;
        this.profiles = profiles;
        history = new HudEditorHistory(registry);
    }

    void resize(int screenWidth, int screenHeight, float pixelScale) {
        cancelGesture();
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        this.pixelScale = pixelScale;
        renderer.resize(pixelScale);
        layouts.clear();
    }

    void open() {
        if (opened) {
            return;
        }
        opened = true;
        profileId = profiles.snapshot().activeProfileId();
        configs.addListener(configListener);
        profiles.addListener(profileListener);
        registry.setEditing(true);
    }

    void close() {
        if (opened) {
            configs.removeListener(configListener);
            profiles.removeListener(profileListener);
        }
        opened = false;
        live = false;
        invalidate();
        selected.clear();
        primary = null;
        registry.setEditing(false);
    }

    void dispose() {
        close();
        renderer.close();
    }

    private void profileChanged() {
        UUID current = profiles.snapshot().activeProfileId();
        if (!Objects.equals(profileId, current)) {
            profileId = current;
            invalidate();
        }
    }

    private void invalidate() {
        cancelGesture();
        history.clear();
        layouts.clear();
    }

    boolean busy() {
        return gesture != Gesture.NONE;
    }

    boolean pointerActive() {
        return gesture == Gesture.MOVE || gesture == Gesture.MARQUEE;
    }

    boolean live() {
        return live;
    }

    void preview(boolean live) {
        this.live = live && !busy();
        registry.setEditing(!this.live);
    }

    void select(HudElement element, boolean additive) {
        finishNudge();
        if (!additive) {
            selected.clear();
        }
        if (!additive || !selected.remove(element)) {
            selected.add(element);
            primary = element;
        } else {
            primary = null;
            for (HudElement remaining : selected) {
                primary = remaining;
            }
        }
    }

    HudBounds bounds(HudElement element) {
        HudBounds original = origins.get(element);
        if (original != null) {
            return original.at(original.x + deltaX, original.y + deltaY);
        }
        HudBounds bounds = layouts.get(element);
        if (bounds == null) {
            bounds = HudRenderer.layout(element, screenWidth, screenHeight, pixelScale, true);
            layouts.put(element, bounds);
        }
        return bounds;
    }

    void render(int mouseX, int mouseY, float partialTicks) {
        boolean removed = selected.removeIf(element -> !registry.contains(element));
        if (removed) {
            invalidate();
            primary = null;
            for (HudElement element : selected) {
                primary = element;
            }
        }
        layouts.clear();
        if (live) {
            return;
        }
        renderState.begin();
        try {
            renderer.rect(0, 0, screenWidth, screenHeight, 0x26000000);
            for (HudElement element : registry.elements()) {
                if (!selected.contains(element)) {
                    renderElement(element, mouseX, mouseY, partialTicks);
                }
            }
            for (HudElement element : selected) {
                if (element != primary) {
                    renderElement(element, mouseX, mouseY, partialTicks);
                }
            }
            if (primary != null) {
                renderElement(primary, mouseX, mouseY, partialTicks);
            }
            if (gesture == Gesture.MARQUEE && moved) {
                int x = Math.min(pressX, pointerX);
                int y = Math.min(pressY, pointerY);
                int width = Math.abs(pointerX - pressX);
                int height = Math.abs(pointerY - pressY);
                renderer.rect(x, y, width, height, 0x2026CEAA);
                outline(x, y, width, height, 0xFF26CEAA);
            }
            if (gesture == Gesture.MOVE && moved) {
                guide(snap.horizontal.active, true);
                guide(snap.vertical.active, false);
            }
        } finally {
            renderState.end();
        }
    }

    void mousePressed(int mouseX, int mouseY, boolean ctrl) {
        if (live) {
            return;
        }
        finishNudge();
        HudElement hit = hit(mouseX, mouseY);
        pressX = pointerX = mouseX;
        pressY = pointerY = mouseY;
        moved = false;
        if (hit == null) {
            additive = ctrl;
            if (!ctrl) {
                selected.clear();
                primary = null;
            }
            gesture = Gesture.MARQUEE;
            return;
        }
        if (ctrl || !selected.contains(hit)) {
            select(hit, ctrl);
        } else {
            primary = hit;
        }
        if (!selected.contains(hit)) {
            return;
        }
        captureOrigins();
        List<HudBounds> others = new ArrayList<>();
        for (HudElement element : registry.elements()) {
            if (!selected.contains(element)) {
                others.add(bounds(element));
            }
        }
        snap.prepare(group, screenWidth, screenHeight, others);
        gesture = Gesture.MOVE;
    }

    void mouseDragged(int mouseX, int mouseY, boolean alt) {
        if (gesture != Gesture.MOVE && gesture != Gesture.MARQUEE) {
            return;
        }
        pointerX = mouseX;
        pointerY = mouseY;
        if (!moved && Math.max(Math.abs(mouseX - pressX), Math.abs(mouseY - pressY)) <= 3) {
            return;
        }
        if (!moved && gesture == Gesture.MOVE) {
            history.begin(selected);
        }
        moved = true;
        if (gesture == Gesture.MOVE) {
            deltaX = snap.horizontal.resolve(group.x + mouseX - pressX, alt) - group.x;
            deltaY = snap.vertical.resolve(group.y + mouseY - pressY, alt) - group.y;
        }
    }

    void mouseReleased() {
        if (!pointerActive()) {
            return;
        }
        if (gesture == Gesture.MOVE && moved) {
            commitMove();
        } else if (gesture == Gesture.MARQUEE && moved) {
            if (!additive) {
                selected.clear();
            }
            int left = Math.min(pressX, pointerX);
            int top = Math.min(pressY, pointerY);
            int right = Math.max(pressX, pointerX);
            int bottom = Math.max(pressY, pointerY);
            for (HudElement element : registry.elements()) {
                HudBounds bounds = bounds(element);
                if (bounds.x < right && bounds.x + bounds.width > left && bounds.y < bottom
                        && bounds.y + bounds.height > top) {
                    selected.add(element);
                    primary = element;
                }
            }
        }
        cancelGesture();
    }

    boolean cancelGesture() {
        boolean pending = busy();
        gesture = Gesture.NONE;
        origins.clear();
        deltaX = deltaY = 0;
        moved = false;
        history.discard();
        snap.release();
        return pending;
    }

    void nudge(int dx, int dy) {
        if (live || selected.isEmpty() || gesture != Gesture.NONE && gesture != Gesture.NUDGE) {
            return;
        }
        if (gesture == Gesture.NONE) {
            captureOrigins();
            history.begin(selected);
            gesture = Gesture.NUDGE;
        }
        deltaX = clamp(group.x + deltaX + dx, 0, Math.max(0, screenWidth - group.width)) - group.x;
        deltaY = clamp(group.y + deltaY + dy, 0, Math.max(0, screenHeight - group.height))
                - group.y;
    }

    void finishNudge() {
        if (gesture == Gesture.NUDGE) {
            commitMove();
            cancelGesture();
        }
    }

    private void captureOrigins() {
        for (HudElement element : selected) {
            origins.put(element, bounds(element));
        }
        int left = screenWidth;
        int top = screenHeight;
        int right = 0;
        int bottom = 0;
        for (HudBounds bounds : origins.values()) {
            left = Math.min(left, bounds.x);
            top = Math.min(top, bounds.y);
            right = Math.max(right, bounds.x + bounds.width);
            bottom = Math.max(bottom, bounds.y + bounds.height);
        }
        group = new HudEditorSnap.Box(left, top, right - left, bottom - top);
        deltaX = deltaY = 0;
    }

    private void commitMove() {
        applying = true;
        try {
            for (Map.Entry<HudElement, HudBounds> entry : origins.entrySet()) {
                HudBounds origin = entry.getValue();
                if (deltaX != 0 || deltaY != 0) {
                    HudElement element = entry.getKey();
                    int x = origin.x + deltaX;
                    int y = origin.y + deltaY;
                    HudPlacement placement = element.config().placement();
                    if (placement.automatic()) {
                        element.config().placement(HudPlacement.fromOrigin(x, y, origin.width,
                                origin.height, screenWidth, screenHeight));
                    } else {
                        HudAnchor anchor = placement.anchor();
                        element.config().placement(new HudPlacement(anchor,
                                x + anchor.elementX(origin.width) - anchor.screenX(screenWidth),
                                y + anchor.elementY(origin.height) - anchor.screenY(screenHeight),
                                false));
                    }
                }
            }
            history.commit();
        } finally {
            applying = false;
        }
        layouts.clear();
    }

    private void scale(int value) {
        change(selected, () -> primary.config().scale().set(value));
    }

    void mouseWheel(int mouseX, int mouseY, int delta, boolean fine) {
        if (live || busy() || selected.size() != 1 || !contains(bounds(primary), mouseX, mouseY)) {
            return;
        }
        scale(clamp(primary.config().scale().get() + Integer.signum(delta) * (fine ? 1 : 5),
                primary.config().scale().minimum(), primary.config().scale().maximum()));
    }

    void reset(boolean all) {
        Iterable<HudElement> elements = all ? registry.elements() : selected;
        change(elements, () -> {
            for (HudElement element : elements) {
                HudConfig config = element.config();
                config.placement(config.defaultPlacement());
                config.scale().set(config.scale().defaultValue());
            }
        });
    }

    void undo() {
        finishNudge();
        applying = true;
        try {
            history.undo();
        } finally {
            applying = false;
        }
        layouts.clear();
    }

    void redo() {
        finishNudge();
        applying = true;
        try {
            history.redo();
        } finally {
            applying = false;
        }
        layouts.clear();
    }

    private void change(Iterable<HudElement> elements, Runnable action) {
        finishNudge();
        history.begin(elements);
        applying = true;
        try {
            action.run();
            history.commit();
        } finally {
            applying = false;
        }
        layouts.clear();
    }

    private HudElement hit(int x, int y) {
        if (primary != null && contains(bounds(primary), x, y)) {
            return primary;
        }
        HudElement hit = null;
        for (HudElement element : selected) {
            if (contains(bounds(element), x, y)) {
                hit = element;
            }
        }
        if (hit != null) {
            return hit;
        }
        List<HudElement> elements = registry.elements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            HudElement element = elements.get(index);
            if (contains(bounds(element), x, y)) {
                return element;
            }
        }
        return null;
    }

    private void renderElement(HudElement element, int mouseX, int mouseY, float partialTicks) {
        HudBounds bounds = bounds(element);
        renderer.render(element, bounds, partialTicks, true);
        boolean hovered = contains(bounds, mouseX, mouseY);
        int color = selected.contains(element) ? 0xFF26CEAA : hovered ? 0xFFD1D1D1 : 0x80909090;
        outline(bounds.x, bounds.y, bounds.width, bounds.height, color);
        if (element == primary || hovered && !busy()) {
            String label = element.displayName() + "  " + element.config().scale().get() + "%";
            int labelX = clamp(bounds.x, 1,
                    Math.max(1, screenWidth - renderer.textWidth(label, false) - 1));
            int fontHeight = renderer.fontHeight(label, false);
            int labelY = clamp(
                    bounds.y >= fontHeight + 3 ? bounds.y - fontHeight - 2
                            : bounds.y + bounds.height + 2,
                    1, Math.max(1, screenHeight - fontHeight - 1));
            renderer.text(label, labelX, labelY, 0xFFF0F0F0, true, false);
        }
    }

    private void guide(HudEditorSnap.Target target, boolean horizontal) {
        if (target == null) {
            return;
        }
        int start = Math.min(target.start, horizontal ? group.y + deltaY : group.x + deltaX);
        int end = Math.max(target.end,
                horizontal ? group.y + deltaY + group.height : group.x + deltaX + group.width);
        if (horizontal) {
            renderer.rect(Math.min(screenWidth - 1, target.line), start, 1, end - start,
                    0xCC26CEAA);
        } else {
            renderer.rect(start, Math.min(screenHeight - 1, target.line), end - start, 1,
                    0xCC26CEAA);
        }
        if (target.gap) {
            renderer.text("6", horizontal ? target.line + 2 : start + 2,
                    horizontal ? start + 2 : target.line + 2, 0xFF26CEAA, true, false);
        }
    }

    private void outline(int x, int y, int width, int height, int color) {
        renderer.rect(x, y, width, 1, color);
        renderer.rect(x, y + height - 1, width, 1, color);
        renderer.rect(x, y, 1, height, color);
        renderer.rect(x + width - 1, y, 1, height, color);
    }

    private static boolean contains(HudBounds bounds, int x, int y) {
        return x >= bounds.x && y >= bounds.y && x < bounds.x + bounds.width
                && y < bounds.y + bounds.height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
