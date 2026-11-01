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
package pit12.runtime.player;

import net.minecraft.entity.player.EntityPlayer;

/** Use this state on the client thread. Values are in HP. */
public final class PlayerHealth {
    private float value;
    private String text;

    public PlayerHealth(float value) {
        this.value = value;
    }

    public static PlayerHealth of(EntityPlayer player) {
        return ((PlayerHealthBinding) player).pit12$health();
    }

    public float value() {
        return value;
    }

    public String text() {
        if (text == null) {
            text = Float.toString(Math.round(value * 10.0F) / 10.0F);
        }
        return text;
    }

    public void update(float health) {
        if (value != health) {
            value = health;
            text = null;
        }
    }
}
