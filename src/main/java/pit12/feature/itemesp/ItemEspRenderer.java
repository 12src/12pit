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
package pit12.feature.itemesp;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import pit12.shared.rendering.BoxRenderer;

final class ItemEspRenderer {
    private final ItemEspConfig config;
    private final BoxRenderer boxes = new BoxRenderer();

    ItemEspRenderer(ItemEspConfig config) {
        this.config = config;
    }

    void render(Minecraft minecraft, List<ItemEspFeature.Target> targets, float partialTicks) {
        if (minecraft.getRenderViewEntity() == null) {
            return;
        }
        int raffleColor = config.raffleColor();
        int goldColor = config.goldColor();
        RenderManager manager = minecraft.getRenderManager();
        boxes.beginFrame(manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ);
        for (int index = 0; index < targets.size(); index++) {
            ItemEspFeature.Target target = targets.get(index);
            EntityItem entity = target.entity;
            if (entity.isDead) {
                continue;
            }
            boolean raffle = target.item == Items.name_tag;
            int color = raffle ? raffleColor : goldColor;
            if ((color >>> 24) == 0) {
                continue;
            }
            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
            double radius = raffle ? 0.2D : 0.25D;
            boxes.add(x - radius, y, z - radius, x + radius, y + radius * 2.0D, z + radius, color,
                    raffle ? 1.0F : 0.8F, 0.0F);
        }
        boxes.render();
    }

    void clear() {
        boxes.clear();
    }
}
