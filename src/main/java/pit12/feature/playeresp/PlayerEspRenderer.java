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
package pit12.feature.playeresp;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import pit12.shared.rendering.BoxRenderer;

final class PlayerEspRenderer {
    private final PlayerEspConfig config;
    private final BoxRenderer boxes = new BoxRenderer();

    PlayerEspRenderer(PlayerEspConfig config) {
        this.config = config;
    }

    void render(Minecraft minecraft, List<PlayerEspFeature.Target> targets, float partialTicks) {
        EntityPlayer localPlayer = minecraft.thePlayer;
        if (localPlayer == null || minecraft.getRenderViewEntity() == null) {
            return;
        }
        int friendColor = config.friendColor();
        int enemyColor = config.enemyColor();
        int otherColor = config.otherColor();
        boolean fade = config.distanceFade();
        int near = Math.min(config.fadeNearDistance(), config.fadeFarDistance());
        int far = Math.max(config.fadeNearDistance(), config.fadeFarDistance());
        double localX = localPlayer.lastTickPosX
                + (localPlayer.posX - localPlayer.lastTickPosX) * partialTicks;
        double localY = localPlayer.lastTickPosY
                + (localPlayer.posY - localPlayer.lastTickPosY) * partialTicks;
        double localZ = localPlayer.lastTickPosZ
                + (localPlayer.posZ - localPlayer.lastTickPosZ) * partialTicks;
        RenderManager manager = minecraft.getRenderManager();
        boxes.beginFrame(manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ);
        for (int index = 0; index < targets.size(); index++) {
            PlayerEspFeature.Target target = targets.get(index);
            EntityPlayer player = target.player;
            if (player == localPlayer || !player.isEntityAlive()) {
                continue;
            }
            int color;
            switch (target.relation) {
                case FRIEND:
                    color = friendColor;
                    break;
                case ENEMY:
                    color = enemyColor;
                    break;
                default:
                    color = otherColor;
            }
            if ((color >>> 24) == 0) {
                continue;
            }
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
            float opacity = 1.0F;
            if (fade) {
                double dx = x - localX;
                double dy = y - localY;
                double dz = z - localZ;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared <= near * near) {
                    continue;
                }
                if (distanceSquared < far * far) {
                    opacity = (float) ((Math.sqrt(distanceSquared) - near) / (far - near));
                }
            }
            double radius = player.width * 0.5D + 0.1D;
            boxes.add(x - radius, y, z - radius, x + radius, y + player.height + 0.2D, z + radius,
                    color, opacity * 0.5F, opacity);
        }
        boxes.render();
    }

    void clear() {
        boxes.clear();
    }
}
