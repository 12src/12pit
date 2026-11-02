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
package pit12.feature.render.nametag;

import java.util.Objects;
import net.minecraft.client.gui.FontRenderer;
import pit12.shared.rendering.FontWidths;

final class NametagText {
    String heldItem;
    String leggings;
    int heldItemWidth;
    int leggingsWidth;
    String name;
    int nameWidth;
    String score;
    int scoreWidth;
    float x;
    float y;
    float scale;

    NametagText(String heldItem, String leggings, FontRenderer font) {
        updateEnchantments(heldItem, leggings, font);
    }

    void updateEnchantments(String heldItem, String leggings, FontRenderer font) {
        if (!Objects.equals(this.heldItem, heldItem)) {
            this.heldItem = heldItem;
            heldItemWidth = heldItem == null ? 0 : FontWidths.measure(font, heldItem);
        }
        if (!Objects.equals(this.leggings, leggings)) {
            this.leggings = leggings;
            leggingsWidth = leggings == null ? 0 : FontWidths.measure(font, leggings);
        }
    }

    void updateName(String name, String score, FontRenderer font) {
        if (!name.equals(this.name)) {
            this.name = name;
            nameWidth = FontWidths.measure(font, name);
        }
        if (score == null) {
            this.score = null;
            scoreWidth = 0;
        } else if (!score.equals(this.score)) {
            this.score = score;
            scoreWidth = FontWidths.measure(font, score);
        }
    }
}
