/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.runtime.hud;

import pit12.runtime.config.HudConfig;

public interface HudElement {
    /** This ID remains stable for the element's registration lifetime. */
    String id();

    /** This name and {@link #config()} remain non-null for the registration lifetime. */
    String displayName();

    boolean enabled();

    HudConfig config();

    /** Pixel scale includes both Minecraft GUI scale and this HUD's configured scale. */
    void resize(float pixelScale);

    /** Width remains inexpensive to query each frame and matches the unscaled render bounds. */
    int width();

    /** Height remains inexpensive to query each frame and matches the unscaled render bounds. */
    int height();

    /**
     * Rendering starts at logical origin 0,0 under a caller-owned transform and GL state guard. Implementations provide
     * representative content while editing even when the live HUD is disabled or has no current data.
     */
    void render(float partialTicks, boolean editing);
}
