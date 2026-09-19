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
package pit12.feature.clickgui;

import java.util.HashMap;
import java.util.Map;

public final class ClickGuiState {
    public static final class Position {
        private int x;
        private int y;

        private Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }
    }

    private final Map<String, Position> positions = new HashMap<String, Position>();

    public Position position(String frameId, int defaultX, int defaultY) {
        Position position = positions.get(frameId);
        if (position == null) {
            position = new Position(defaultX, defaultY);
            positions.put(frameId, position);
        }
        return position;
    }

    public void move(String frameId, int x, int y) {
        Position position = positions.get(frameId);
        if (position == null) {
            positions.put(frameId, new Position(x, y));
        } else {
            position.x = x;
            position.y = y;
        }
    }
}
