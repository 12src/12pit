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
package pit12.feature.clickgui.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import pit12.Pit12;
import pit12.feature.clickgui.ClickGuiConfig;
import pit12.shared.rendering.UiRenderState;
import pit12.shared.rendering.UiRenderer;

public final class ClickGuiRenderer {
    public enum TextureIcon {
        SETTINGS("settings"),
        GEAR("gear"),
        BIND("bind"),
        EDIT("edit"),
        HUD_EDITOR("hud-editor"),
        CLOSE("close"),
        COLLAPSE("collapse"),
        EXPAND("expand"),
        RIGHT("right"),
        SELECTOR("selector"),
        ADD("add"),
        DELETE("delete"),
        SEARCH("search");

        private final ResourceLocation location;

        TextureIcon(String name) {
            location = new ResourceLocation(Pit12.MOD_ID, "textures/gui/clickgui/" + name + ".png");
        }
    }

    private static final int CORNER_SEGMENTS = 12;
    private static final int ROUNDED_POINT_COUNT = 4 * (CORNER_SEGMENTS + 1);
    private static final float EDGE_FEATHER = 1.0F;
    private static final float[] ROUNDED_COS = new float[ROUNDED_POINT_COUNT];
    private static final float[] ROUNDED_SIN = new float[ROUNDED_POINT_COUNT];
    private static final ResourceLocation FONT_LOCATION =
            new ResourceLocation(Pit12.MOD_ID, "fonts/montserrat-regular.ttf");
    private static final ResourceLocation FALLBACK_FONT_LOCATION =
            new ResourceLocation(Pit12.MOD_ID, "fonts/noto-sans-sc.otf");
    static {
        for (int corner = 0; corner < 4; corner++) {
            double start = Math.PI + corner * Math.PI / 2.0;
            if (corner == 1) {
                start = -Math.PI / 2.0;
            }
            for (int step = 0; step <= CORNER_SEGMENTS; step++) {
                double angle = start + step * Math.PI / 2.0 / CORNER_SEGMENTS;
                int index = corner * (CORNER_SEGMENTS + 1) + step;
                ROUNDED_COS[index] = (float) Math.cos(angle);
                ROUNDED_SIN[index] = (float) Math.sin(angle);
            }
        }
    }
    private final Minecraft minecraft;
    private final UiRenderer ui;
    private final ClickGuiTextureCache textures;
    private final UiRenderState renderState = new UiRenderState();
    private final ScissorStack scissors;
    private final ClickGuiConfig config;
    private boolean drawing;
    private float pixelScale = Float.NaN;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    public ClickGuiRenderer(Minecraft minecraft, ClickGuiConfig config) {
        this.minecraft = minecraft;
        this.config = config;
        ui = new UiRenderer(minecraft, FONT_LOCATION, FALLBACK_FONT_LOCATION);
        textures = new ClickGuiTextureCache(minecraft);
        scissors = new ScissorStack(minecraft);
    }

    public void resize(int viewportWidth, int viewportHeight, float interfaceScale,
            float pixelScale) {
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        scissors.setInterfaceScale(interfaceScale);
        float normalizedScale = Math.max(0.01F, pixelScale);
        if (Float.compare(this.pixelScale, normalizedScale) != 0) {
            textures.close();
            this.pixelScale = normalizedScale;
        }
        ui.resize(normalizedScale);
    }

    public void begin() {
        if (drawing) {
            throw new IllegalStateException("Renderer is already active");
        }
        int accent = config.guiColor().get().intValue();
        ClickGuiTheme.configureAccent(accent);
        renderState.begin();
        try {
            scissors.begin();
            drawing = true;
        } catch (RuntimeException failure) {
            renderState.end();
            throw failure;
        }
    }

    public void end() {
        if (!drawing) {
            return;
        }
        try {
            scissors.restore();
        } finally {
            renderState.end();
            drawing = false;
        }
    }

    public void rect(int x, int y, int width, int height, int color) {
        ui.rect(x, y, width, height, color);
    }

    public void roundedRect(int x, int y, int width, int height, int radius, int color) {
        roundedRect((float) x, (float) y, (float) width, (float) height, (float) radius, color);
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        float actualRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        if (actualRadius == 0.0F) {
            rect(Math.round(x), Math.round(y), Math.round(width), Math.round(height), color);
            return;
        }
        beginShape();
        drawRoundedFill(x, y, width, height, actualRadius, color);
        endShape();
    }

    public void roundedOutline(float x, float y, float width, float height, float radius,
            float thickness, int color) {
        if (width <= 0.0F || height <= 0.0F || thickness <= 0.0F) {
            return;
        }
        float actualRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        float actualThickness = Math.min(thickness, Math.min(width, height) / 2.0F);
        beginShape();
        drawRoundedRing(x, y, width, height, actualRadius, actualThickness, color);
        endShape();
    }

    public void outline(int x, int y, int width, int height, int color) {
        rect(x, y, width, 1, color);
        rect(x, y + height - 1, width, 1, color);
        rect(x, y, 1, height, color);
        rect(x + width - 1, y, 1, height, color);
    }

    public void settingsIcon(int x, int y, int color) {
        texture(TextureIcon.GEAR, x, y, 8, 8, color);
    }

    public void searchIcon(int x, int y, int color) {
        texture(TextureIcon.SEARCH, x, y, 8, 8, color);
    }

    public void horizontalGradient(int x, int y, int width, int height, int leftColor,
            int rightColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glBegin(GL11.GL_QUADS);
        glColor(leftColor);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + height);
        glColor(rightColor);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public int mix(int from, int to, float progress) {
        float amount = Math.max(0.0F, Math.min(1.0F, progress));
        int alpha = mixChannel(from >>> 24, to >>> 24, amount);
        int red = mixChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF, amount);
        int green = mixChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF, amount);
        int blue = mixChannel(from & 0xFF, to & 0xFF, amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    // Rasterized icons are reused until the framebuffer scale changes.
    public void texture(TextureIcon icon, int x, int y, int width, int height, int color) {
        texture(textures.prepare(icon.location, Math.max(1, Math.round(width * pixelScale)),
                Math.max(1, Math.round(height * pixelScale))), x, y, width, height, color);
    }

    public void centeredTexture(TextureIcon icon, float x, int y, int containerHeight, int width,
            int height, int color) {
        ResourceLocation texture =
                textures.prepare(icon.location, Math.max(1, Math.round(width * pixelScale)),
                        Math.max(1, Math.round(height * pixelScale)));
        texture(texture, x, y + (containerHeight - height) / 2.0F, width, height, color);
    }

    public boolean animationsEnabled() {
        return config.animations().get().booleanValue();
    }

    public void scrollbar(int x, int y, int trackHeight, int viewportHeight, int contentHeight,
            int scroll) {
        if (!config.scrollbars().get().booleanValue() || trackHeight <= 0
                || contentHeight <= viewportHeight) {
            return;
        }
        int thumbHeight = Math.max(10, trackHeight * viewportHeight / contentHeight);
        int travel = trackHeight - thumbHeight;
        int maximumScroll = contentHeight - viewportHeight;
        int thumbY = y + (maximumScroll == 0 ? 0 : travel * scroll / maximumScroll);
        rect(x, y, 1, trackHeight, ClickGuiTheme.DIVIDER);
        rect(x - 1, thumbY, 2, thumbHeight, ClickGuiTheme.MUTED_TEXT);
    }

    public void text(String text, int x, int y, float fontSize, int color) {
        ui.text(text, x, y, fontSize, color);
    }

    public void verticallyCenteredText(String text, int x, int y, int height, float fontSize,
            int color) {
        text(text, x, y + Math.round((height - fontHeight(fontSize)) / 2.0F), fontSize, color);
    }

    public void centeredText(String text, int x, int y, int width, int height, float fontSize,
            int color) {
        text(text, x + (width - textWidth(text, fontSize)) / 2,
                y + Math.round((height - fontHeight(fontSize)) / 2.0F), fontSize, color);
    }

    public int textWidth(String text, float fontSize) {
        return ui.textWidth(text, fontSize);
    }

    public int fontHeight(float fontSize) {
        return ui.fontHeight(fontSize);
    }

    public void close() {
        textures.close();
        ui.close();
        pixelScale = Float.NaN;
    }

    public String ellipsize(String text, int maximumWidth, float fontSize) {
        if (textWidth(text, fontSize) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        int available = maximumWidth - textWidth(suffix, fontSize);
        if (available <= 0) {
            return suffix;
        }
        int length = text.length();
        while (length > 0 && textWidth(text.substring(0, length), fontSize) > available) {
            length--;
        }
        return text.substring(0, length) + suffix;
    }

    public void pushScissor(int x, int y, int width, int height) {
        scissors.push(x, y, width, height);
    }

    public void popScissor() {
        scissors.pop();
    }

    public void tooltip(String text, int mouseX, int mouseY) {
        int maximumTextWidth = Math.max(40, Math.min(220, viewportWidth - 16));
        List<String> lines = new ArrayList<String>();
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
            } else {
                lines.addAll(wrap(paragraph, maximumTextWidth));
            }
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, textWidth(line, 8.0F));
        }
        int height = lines.size() * (fontHeight(8.0F) + 2) + 6;
        int x = Math.max(2, Math.min(mouseX + 8, viewportWidth - width - 10));
        int y = Math.max(2, Math.min(mouseY + 8, viewportHeight - height - 2));
        roundedRect(x, y, width + 8, height, 4, ClickGuiTheme.PANEL_INSET);
        roundedOutline(x - 0.5F, y - 0.5F, width + 9.0F, height + 1.0F, 4.1F, 1.0F,
                ClickGuiTheme.BORDER);
        for (int index = 0; index < lines.size(); index++) {
            text(lines.get(index), x + 4, y + 4 + index * (fontHeight(8.0F) + 2), 8.0F,
                    ClickGuiTheme.TEXT);
        }
    }

    private List<String> wrap(String text, int maximumWidth) {
        ArrayList<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && textWidth(candidate, 8.0F) > maximumWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private void texture(ResourceLocation texture, float x, float y, int width, int height,
            int color) {
        if (width <= 0 || height <= 0 || Float.isNaN(pixelScale)) {
            return;
        }
        int pixelX = Math.round(x * pixelScale);
        int pixelY = Math.round(y * pixelScale);
        int pixelWidth = Math.max(1, Math.round(width * pixelScale));
        int pixelHeight = Math.max(1, Math.round(height * pixelScale));
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F / pixelScale, 1.0F / pixelScale, 1.0F);
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1,
                    0);
            minecraft.getTextureManager().bindTexture(texture);
            GlStateManager.color(red, green, blue, alpha);
            Gui.drawModalRectWithCustomSizedTexture(pixelX, pixelY, 0.0F, 0.0F, pixelWidth,
                    pixelHeight, pixelWidth, pixelHeight);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static int mixChannel(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }

    private static void beginShape() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private static void endShape() {
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawRoundedFill(float x, float y, float width, float height, float radius,
            int color) {
        float halfFeather = EDGE_FEATHER / 2.0F;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(color);
        GL11.glVertex2f(x + width / 2.0F, y + height / 2.0F);
        for (int index = 0; index <= ROUNDED_POINT_COUNT; index++) {
            emitRoundedVertex(x, y, width, height, radius, -halfFeather,
                    index % ROUNDED_POINT_COUNT);
        }
        GL11.glEnd();
        int transparent = withAlpha(color, 0);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int index = 0; index <= ROUNDED_POINT_COUNT; index++) {
            int point = index % ROUNDED_POINT_COUNT;
            glColor(color);
            emitRoundedVertex(x, y, width, height, radius, -halfFeather, point);
            glColor(transparent);
            emitRoundedVertex(x, y, width, height, radius, halfFeather, point);
        }
        GL11.glEnd();
    }

    private static void drawRoundedRing(float x, float y, float width, float height, float radius,
            float thickness, int color) {
        float feather = Math.min(EDGE_FEATHER, thickness);
        float halfFeather = feather / 2.0F;
        float outerCore = -halfFeather;
        float innerCore = -thickness + halfFeather;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        glColor(color);
        for (int index = 0; index <= ROUNDED_POINT_COUNT; index++) {
            int point = index % ROUNDED_POINT_COUNT;
            emitRoundedVertex(x, y, width, height, radius, outerCore, point);
            emitRoundedVertex(x, y, width, height, radius, innerCore, point);
        }
        GL11.glEnd();
        int transparent = withAlpha(color, 0);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int index = 0; index <= ROUNDED_POINT_COUNT; index++) {
            int point = index % ROUNDED_POINT_COUNT;
            glColor(color);
            emitRoundedVertex(x, y, width, height, radius, outerCore, point);
            glColor(transparent);
            emitRoundedVertex(x, y, width, height, radius, halfFeather, point);
        }
        GL11.glEnd();
        float innerFeather = Math.min(feather, Math.max(0.0F, radius - thickness));
        if (innerFeather == 0.0F) {
            return;
        }
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int index = 0; index <= ROUNDED_POINT_COUNT; index++) {
            int point = index % ROUNDED_POINT_COUNT;
            glColor(color);
            emitRoundedVertex(x, y, width, height, radius, innerCore, point);
            glColor(transparent);
            emitRoundedVertex(x, y, width, height, radius, -thickness - innerFeather / 2.0F, point);
        }
        GL11.glEnd();
    }

    private static void emitRoundedVertex(float x, float y, float width, float height, float radius,
            float offset, int point) {
        int corner = point / (CORNER_SEGMENTS + 1);
        float centerX = corner == 0 || corner == 3 ? x + radius : x + width - radius;
        float centerY = corner < 2 ? y + radius : y + height - radius;
        float adjustedRadius = Math.max(0.0F, radius + offset);
        GL11.glVertex2f(centerX + ROUNDED_COS[point] * adjustedRadius,
                centerY + ROUNDED_SIN[point] * adjustedRadius);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private static void glColor(int color) {
        GlStateManager.color((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24 & 0xFF) / 255.0F);
    }
}
