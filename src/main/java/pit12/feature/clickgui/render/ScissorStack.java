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
package pit12.feature.clickgui.render;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class ScissorStack {
    // LWJGL 2 applies the same 16-element validation to GL_SCISSOR_BOX's four-value query.
    private static final int GL_QUERY_BUFFER_CAPACITY = 16;
    private final Minecraft minecraft;
    private final Deque<Rect> stack = new ArrayDeque<Rect>();
    private final IntBuffer boxBuffer = BufferUtils.createIntBuffer(GL_QUERY_BUFFER_CAPACITY);
    private boolean initiallyEnabled;
    private final int[] initialBox = new int[4];
    private float interfaceScale = 1.0F;
    private float scale;

    public ScissorStack(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void setInterfaceScale(float interfaceScale) {
        this.interfaceScale = Math.max(0.01F, interfaceScale);
    }

    public void begin() {
        if (!stack.isEmpty()) {
            throw new IllegalStateException("Scissor stack was not restored");
        }
        initiallyEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boxBuffer.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, boxBuffer);
        for (int index = 0; index < initialBox.length; index++) {
            initialBox[index] = boxBuffer.get(index);
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        scale = resolution.getScaleFactor() * interfaceScale;
    }

    public void push(int x, int y, int width, int height) {
        Rect requested = new Rect(x, y, Math.max(0, width), Math.max(0, height));
        Rect clipped = stack.isEmpty() ? requested : requested.intersection(stack.peek());
        stack.push(clipped);
        apply(clipped);
    }

    public void pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Scissor stack is empty");
        }
        stack.pop();
        if (stack.isEmpty()) {
            if (initiallyEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(initialBox[0], initialBox[1], initialBox[2], initialBox[3]);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        } else {
            apply(stack.peek());
        }
    }

    public void restore() {
        stack.clear();
        if (initiallyEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(initialBox[0], initialBox[1], initialBox[2], initialBox[3]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void apply(Rect rect) {
        int left = Math.max(0, (int) Math.floor(rect.x * scale));
        int top = Math.max(0, (int) Math.floor(rect.y * scale));
        int right =
                Math.min(minecraft.displayWidth, (int) Math.ceil((rect.x + rect.width) * scale));
        int bottom =
                Math.min(minecraft.displayHeight, (int) Math.ceil((rect.y + rect.height) * scale));
        int framebufferY = minecraft.displayHeight - bottom;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, Math.max(0, framebufferY), Math.max(0, right - left),
                Math.max(0, bottom - top));
    }

    private static final class Rect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private Rect intersection(Rect other) {
            int left = Math.max(x, other.x);
            int top = Math.max(y, other.y);
            int right = Math.min(x + width, other.x + other.width);
            int bottom = Math.min(y + height, other.y + other.height);
            return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }
}
