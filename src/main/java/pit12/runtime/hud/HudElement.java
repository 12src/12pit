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
package pit12.runtime.hud;

import pit12.runtime.config.HudConfig;

public interface HudElement {
    /** Keep this ID stable while registered. */
    String id();

    /** The name and {@link #config()} must stay non-null while registered. */
    String displayName();

    boolean enabled();

    HudConfig config();

    /** Pixel scale includes both Minecraft GUI scale and this HUD's configured scale. */
    void resize(float pixelScale);

    /** Prepare the current or sample content before its bounds are read. */
    default void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
    }

    /** Return the unscaled render width. Keep this query cheap enough for every frame. */
    int width();

    /** Return the unscaled render height. Keep this query cheap enough for every frame. */
    int height();

    /**
     * Render at (0, 0). The caller manages the transform and GL state. Show sample content while editing, even if the live
     * HUD is disabled or has no data.
     */
    void render(float partialTicks, boolean editing);
}
