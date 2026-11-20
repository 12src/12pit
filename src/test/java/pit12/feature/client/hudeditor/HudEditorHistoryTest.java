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
package pit12.feature.client.hudeditor;

import static org.junit.Assert.assertEquals;
import static pit12.runtime.config.HudAnchor.TOP_LEFT;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudPlacement;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRegistry;
import pit12.shared.concurrent.ClientThread;

public final class HudEditorHistoryTest {
    private final StubHud hud = new StubHud();
    private final HudRegistry registry = new HudRegistry(ClientThread.current());
    private final HudEditorHistory history = new HudEditorHistory(registry);

    @Before
    public void registerHud() {
        registry.register(hud);
    }

    @Test
    public void undoAndRedoRestorePlacementAndScale() {
        history.begin(Collections.singletonList(hud));
        hud.config().placement(new HudPlacement(TOP_LEFT, 12, 8, false));
        hud.config().scale().set(150);
        history.commit();
        history.undo();
        assertEquals(0, hud.config().placement().offsetX());
        assertEquals(0, hud.config().placement().offsetY());
        assertEquals(100, (int) hud.config().scale().get());
        history.redo();
        assertEquals(12, hud.config().placement().offsetX());
        assertEquals(8, hud.config().placement().offsetY());
        assertEquals(150, (int) hud.config().scale().get());
    }

    @Test
    public void committingWithoutChangesPreservesRedoHistory() {
        move(1);
        history.undo();
        history.begin(Collections.singletonList(hud));
        history.commit();
        history.redo();
        assertEquals(1, hud.config().placement().offsetX());
    }

    @Test
    public void aNewEditAfterUndoClearsRedoHistory() {
        move(1);
        history.undo();
        assertEquals(0, hud.config().placement().offsetX());
        move(2);
        history.redo();
        assertEquals(2, hud.config().placement().offsetX());
    }

    private void move(int x) {
        history.begin(Collections.singletonList(hud));
        hud.config().placement(new HudPlacement(TOP_LEFT, x, 0, false));
        history.commit();
    }

    private static final class StubHud extends FeatureConfig implements HudElement {
        private final HudConfig config = hudConfig("hud", TOP_LEFT, 0, 0, false);

        StubHud() {
            super("foo", "Foo", ConfigCategory.RENDER, "");
        }

        @Override
        public HudConfig config() {
            return config;
        }

        @Override
        public void resize(float pixelScale) {}

        @Override
        public int width() {
            return 10;
        }

        @Override
        public int height() {
            return 10;
        }

        @Override
        public void render(float partialTicks, boolean editing) {}
    }
}
