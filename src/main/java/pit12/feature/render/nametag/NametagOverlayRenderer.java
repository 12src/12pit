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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.opengl.GL11;
import pit12.shared.rendering.TextureState;
import pit12.shared.rendering.WorldRenderState;

final class NametagOverlayRenderer {
    private final TextureState textures;
    private final WorldRenderState state;
    private final List<NametagText> visible = new ArrayList<>();
    private ClippingHelper clipping;

    NametagOverlayRenderer(TextureState textures) {
        this.textures = textures;
        state = new WorldRenderState(textures);
    }

    void render(Minecraft minecraft, WorldClient world, Map<UUID, NametagText> texts,
            Function<EntityPlayer, String> suffix, int upwardOffset, float scale,
            float partialTicks) {
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) {
            return;
        }
        if (clipping == null) {
            // Vanilla refreshes this shared helper before every world pass.
            clipping = ClippingHelperImpl.getInstance();
        }
        float[] matrix = clipping.clippingMatrix;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        double width = resolution.getScaledWidth_double() / scale;
        double height = resolution.getScaledHeight_double() / scale;
        // Use vanilla's world scale so the crossover follows the current projection and GUI scale.
        double vanillaScale = NametagRenderer.WORLD_SCALE * Math.abs(clipping.projectionMatrix[5])
                * height * 0.5D;
        FontRenderer font = minecraft.fontRendererObj;
        RenderManager manager = minecraft.getRenderManager();
        float lineHeight = font.FONT_HEIGHT + 2;
        float scoreOffset = font.FONT_HEIGHT * 1.15F;
        float offset = upwardOffset * NametagRenderer.OFFSET_SCALE;
        Scoreboard scoreboard = world.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
        for (int index = 0; index < world.playerEntities.size(); index++) {
            EntityPlayer player = world.playerEntities.get(index);
            if (player.isDead || player == minecraft.thePlayer || player == camera
                    || player.isInvisibleToPlayer(minecraft.thePlayer)) {
                continue;
            }
            boolean sneaking = player.isSneaking();
            double distanceSquared = player.getDistanceSqToEntity(camera);
            float range = sneaking ? RendererLivingEntity.NAME_TAG_RANGE_SNEAK
                    : RendererLivingEntity.NAME_TAG_RANGE;
            if (distanceSquared >= range * range
                    || !((NametagVisibility) manager.getEntityRenderObject(player))
                            .pit12$canRenderName(player)) {
                continue;
            }
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
                    - manager.viewerPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
                    - manager.viewerPosY + player.height + 0.5D - (sneaking ? 0.25D : 0.0D);
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks
                    - manager.viewerPosZ;
            double w = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
            double depth = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
            if (w <= 0.0D || depth < -w || depth > w) {
                continue;
            }
            UUID playerId = player.getUniqueID();
            NametagText text = texts.get(playerId);
            if (text == null) {
                text = new NametagText(null, null, font);
                texts.put(playerId, text);
            }
            String name = player.getDisplayName().getFormattedText();
            if (suffix != null) {
                name += suffix.apply(player);
            }
            String score =
                    !sneaking && distanceSquared < 100.0D && objective != null
                            ? scoreboard.getValueFromObjective(player.getName(), objective)
                                    .getScorePoints() + " " + objective.getDisplayName()
                            : null;
            text.updateName(name, score, font);
            text.scale = (float) Math.max(1.0D, vanillaScale / w);
            text.x = (float) ((1.0D
                    + (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]) / w) * width
                    * 0.5D);
            text.y = (float) ((1.0D
                    - (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]) / w) * height
                    * 0.5D) - (score == null ? 0.0F : scoreOffset * text.scale);
            float halfWidth = (Math.max(Math.max(text.nameWidth, text.scoreWidth),
                    Math.max(text.heldItemWidth, text.leggingsWidth)) / 2 + 1) * text.scale;
            float top = text.y;
            if (text.heldItem != null || text.leggings != null) {
                top -= (offset + (text.heldItem != null && text.leggings != null ? lineHeight : 0))
                        * text.scale;
            }
            float bottom =
                    text.y + ((score == null ? 0 : scoreOffset) + font.FONT_HEIGHT) * text.scale;
            if (text.x + halfWidth >= 0 && text.x - halfWidth <= width && bottom >= 0
                    && top - text.scale <= height) {
                visible.add(text);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        state.begin(GL11.GL_CURRENT_BIT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            GlStateManager.disableCull();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer vertices = tessellator.getWorldRenderer();
            vertices.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int index = 0; index < visible.size(); index++) {
                NametagText text = visible.get(index);
                background(vertices, text.x, text.y, text.nameWidth, text.scale);
                if (text.score != null) {
                    background(vertices, text.x, text.y + scoreOffset * text.scale, text.scoreWidth,
                            text.scale);
                }
                if (text.leggings != null) {
                    background(vertices, text.x, text.y - offset * text.scale, text.leggingsWidth,
                            text.scale);
                }
                if (text.heldItem != null) {
                    background(vertices, text.x, text.y
                            - (offset + (text.leggings == null ? 0 : lineHeight)) * text.scale,
                            text.heldItemWidth, text.scale);
                }
            }
            tessellator.draw();
            textures.setEnabled(true);
            for (int index = 0; index < visible.size(); index++) {
                NametagText text = visible.get(index);
                float x = text.x;
                float y = text.y;
                boolean enlarged = text.scale > 1.0F;
                if (enlarged) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(x, y, 0.0F);
                    GlStateManager.scale(text.scale, text.scale, 1.0F);
                    x = 0.0F;
                    y = 0.0F;
                }
                try {
                    font.drawString(text.name, x - text.nameWidth / 2, y, 0xFFFFFFFF, false);
                    if (text.score != null) {
                        font.drawString(text.score, x - text.scoreWidth / 2, y + scoreOffset,
                                0xFFFFFFFF, false);
                    }
                    if (text.leggings != null) {
                        font.drawString(text.leggings, x - text.leggingsWidth / 2, y - offset,
                                0xFFFFFFFF, false);
                    }
                    if (text.heldItem != null) {
                        font.drawString(text.heldItem, x - text.heldItemWidth / 2,
                                y - offset - (text.leggings == null ? 0 : lineHeight), 0xFFFFFFFF,
                                false);
                    }
                } finally {
                    if (enlarged) {
                        GlStateManager.popMatrix();
                    }
                }
            }
        } finally {
            visible.clear();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            state.end();
        }
    }

    private static void background(WorldRenderer vertices, float x, float y, int width,
            float scale) {
        float halfWidth = (width / 2 + 1) * scale;
        float left = x - halfWidth;
        float right = x + halfWidth;
        float top = y - scale;
        float bottom = y + 8 * scale;
        vertices.pos(left, top, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        vertices.pos(left, bottom, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        vertices.pos(right, bottom, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        vertices.pos(right, top, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
    }
}
