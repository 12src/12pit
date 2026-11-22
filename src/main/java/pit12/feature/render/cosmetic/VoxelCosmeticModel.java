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
package pit12.feature.render.cosmetic;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class VoxelCosmeticModel implements CosmeticModel {
    private static final int[][] DIRECTIONS =
            {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},};
    private static final int[][][] CORNERS = {{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
            {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},};
    private static final float[] SHADE = {0.78F, 0.78F, 0.5F, 1.0F, 0.9F, 0.72F};
    private final int resolution;
    private final int[] palette;
    private final Map<Integer, Integer> voxels = new HashMap<>();
    private int displayList = -1;
    private boolean disposed;

    public VoxelCosmeticModel(int resolution, int[] palette) {
        this.resolution = resolution;
        this.palette = palette.clone();
    }

    public void put(int x, int y, int z, int colorIndex) {
        voxels.put(pack(x, y, z), colorIndex);
    }

    private static int pack(int x, int y, int z) {
        return x + 64 | (y + 64) << 8 | (z + 64) << 16;
    }

    @Override
    public void render() {
        if (disposed) {
            return;
        }
        if (displayList == -1) {
            compile();
        }
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GL11.glCallList(displayList);
    }

    private void compile() {
        displayList = GL11.glGenLists(1);
        GL11.glNewList(displayList, GL11.GL_COMPILE);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer world = tessellator.getWorldRenderer();
        world.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Map.Entry<Integer, Integer> entry : voxels.entrySet()) {
            int key = entry.getKey();
            int x = (key & 255) - 64;
            int y = (key >> 8 & 255) - 64;
            int z = (key >> 16 & 255) - 64;
            int color = palette[entry.getValue()];
            for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                int[] offset = DIRECTIONS[direction];
                if (voxels.containsKey(pack(x + offset[0], y + offset[1], z + offset[2]))) {
                    continue;
                }
                emit(world, x, y, z, direction, color);
            }
        }
        tessellator.draw();
        GL11.glEndList();
    }

    private void emit(WorldRenderer world, int x, int y, int z, int direction, int color) {
        float unit = 1.0F / resolution;
        float red = (color >> 16 & 255) / 255.0F * SHADE[direction];
        float green = (color >> 8 & 255) / 255.0F * SHADE[direction];
        float blue = (color & 255) / 255.0F * SHADE[direction];
        for (int[] corner : CORNERS[direction]) {
            world.pos((x + corner[0]) * unit, (y + corner[1]) * unit, (z + corner[2]) * unit)
                    .color(red, green, blue, 1.0F).endVertex();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        if (displayList != -1) {
            GL11.glDeleteLists(displayList, 1);
            displayList = -1;
        }
    }
}
