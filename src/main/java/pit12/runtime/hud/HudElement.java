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
import pit12.shared.performance.HotPath;

public interface HudElement {
    /** Keep this ID stable while registered. */
    String id();

    /** Keep the name and {@link #config()} non-null while registered. */
    String displayName();

    boolean enabled();

    HudConfig config();

    /** pixelScale combines GUI scale and this HUD's scale. */
    @HotPath
    void resize(float pixelScale);

    /** Prepare live or sample content before the caller reads its bounds. */
    @HotPath
    default void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
    }

    /** Unscaled width. This gets called every frame, so keep it cheap. */
    @HotPath
    int width();

    /** Unscaled height. This gets called every frame, so keep it cheap. */
    @HotPath
    int height();

    /**
     * Draw at (0, 0). The caller handles transforms and GL state. Show samples while editing, even for disabled HUDs. Live
     * previews use the normal overlay callbacks.
     */
    @HotPath
    void render(float partialTicks, boolean editing);
}
