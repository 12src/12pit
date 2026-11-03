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
package pit12.feature.render.focus;

import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

public final class FocusRenderPass {
    public static boolean enabled;
    public static FocusRenderPass current;
    private Draw[] draws = new Draw[0];
    private int count;
    private float opacity;
    private FocusRenderState state;
    private FocusRenderState shadowState;

    public void begin() {
        current = this;
    }

    public void add(RenderManager manager, Entity entity, double x, double y, double z, float yaw,
            float partialTicks, float opacity) {
        if (count == draws.length) {
            int capacity = Math.max(16, count * 2);
            draws = Arrays.copyOf(draws, capacity);
            for (int index = count; index < capacity; index++)
                draws[index] = new Draw();
        }
        Draw draw = draws[count++];
        draw.manager = manager;
        draw.entity = entity;
        draw.x = x;
        draw.y = y;
        draw.z = z;
        draw.distance = x * x + y * y + z * z;
        draw.yaw = yaw;
        draw.partialTicks = partialTicks;
        draw.lightX = OpenGlHelper.lastBrightnessX;
        draw.lightY = OpenGlHelper.lastBrightnessY;
        this.opacity = opacity;
    }

    public void draw(Consumer<Entity> prepareEntity, boolean shadow) {
        current = null;
        if (count == 0)
            return;
        // Sort in place to avoid a temporary sorting array each frame.
        for (int root = count / 2 - 1; root >= 0; root--)
            siftDown(root, count);
        for (int end = count - 1; end > 0; end--) {
            Draw first = draws[0];
            draws[0] = draws[end];
            draws[end] = first;
            siftDown(0, end);
        }
        FocusRenderState renderState = shadow ? shadowState : state;
        if (renderState == null) {
            renderState = new FocusRenderState();
            if (shadow)
                shadowState = renderState;
            else
                state = renderState;
        }
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;
        renderState.begin(opacity);
        try {
            for (int index = count - 1; index >= 0; index--) {
                Draw draw = draws[index];
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, draw.lightX,
                        draw.lightY);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                if (prepareEntity != null)
                    prepareEntity.accept(draw.entity);
                draw.manager.doRenderEntity(draw.entity, draw.x, draw.y, draw.z, draw.yaw,
                        draw.partialTicks, true);
            }
        } finally {
            try {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX,
                        previousLightY);
                renderState.end();
            } finally {
                clear();
            }
        }
    }

    public void clear() {
        current = null;
        for (int index = 0; index < count; index++) {
            draws[index].entity = null;
            draws[index].manager = null;
        }
        count = 0;
    }

    private void siftDown(int root, int size) {
        Draw value = draws[root];
        for (int child = root * 2 + 1; child < size; child = root * 2 + 1) {
            if (child + 1 < size && draws[child + 1].distance > draws[child].distance)
                child++;
            if (value.distance >= draws[child].distance)
                break;
            draws[root] = draws[child];
            root = child;
        }
        draws[root] = value;
    }

    private static final class Draw {
        RenderManager manager;
        Entity entity;
        double x;
        double y;
        double z;
        double distance;
        float yaw;
        float partialTicks;
        float lightX;
        float lightY;
    }
}
