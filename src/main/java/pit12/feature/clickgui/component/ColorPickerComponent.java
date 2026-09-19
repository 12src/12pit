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
package pit12.feature.clickgui.component;

import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;

public final class ColorPickerComponent extends GuiComponent {
    private static final int COLLAPSED_HEIGHT = 25;
    private static final int CHANNEL_HEIGHT = 25;
    private static final int[] PALETTE =
            {0x078D70, 0x26CEAA, 0x98E8C1, 0xFFFFFF, 0x7BADE2, 0x5049CC, 0x3D1A78};
    private final String label;
    private final IntSupplier value;
    private final IntConsumer setter;
    private final UiAnimation paletteAnimation = new UiAnimation();
    private final UiAnimation[] channelAnimations =
            {new UiAnimation(), new UiAnimation(), new UiAnimation()};
    private boolean expanded;
    private boolean draggingPalette;
    private int draggedChannel = -1;
    private int expandControlX;

    public ColorPickerComponent(String label, IntSupplier value, IntConsumer setter) {
        super(0, 0, 0, COLLAPSED_HEIGHT);
        this.label = label;
        this.value = value;
        this.setter = setter;
    }

    public int preferredHeight() {
        return expanded ? COLLAPSED_HEIGHT + CHANNEL_HEIGHT * 3 : COLLAPSED_HEIGHT;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        int rgb = value.getAsInt() & 0xFFFFFF;
        renderer.verticallyCenteredText(label, x + 5, y, 14, 8.0F, ClickGuiTheme.MUTED_TEXT);
        expandControlX = x + 10 + renderer.textWidth(label, 8.0F);
        renderer.centeredTexture(expanded ? TextureIcon.COLLAPSE : TextureIcon.EXPAND,
                expandControlX, y, 14, 6, 6, ClickGuiTheme.MUTED_TEXT);
        renderPalette(renderer, rgb);
        int previewY = y + Math.round((14 - 7) / 2.0F);
        renderer.rect(x + width - 12, previewY, 7, 7, 0xFF000000 | rgb);
        renderer.outline(x + width - 12, previewY, 7, 7, ClickGuiTheme.BORDER);
        if (!expanded) {
            return;
        }
        float[] hsb = Color.RGBtoHSB(rgb >>> 16 & 0xFF, rgb >>> 8 & 0xFF, rgb & 0xFF, null);
        renderChannel(renderer, "Hue", 0, hsb, hsb[0]);
        renderChannel(renderer, "Saturation", 1, hsb, hsb[1]);
        renderChannel(renderer, "Brightness", 2, hsb, hsb[2]);
    }

    private void renderPalette(ClickGuiRenderer renderer, int rgb) {
        int segmentWidth = Math.max(1, (width - 10) / PALETTE.length);
        int trackWidth = segmentWidth * PALETTE.length;
        int trackX = x + (width - trackWidth) / 2;
        int trackY = y + 17;
        int selected = matchingPaletteIndex(rgb);
        for (int index = 0; index < PALETTE.length; index++) {
            int segmentX = trackX + index * segmentWidth;
            renderer.rect(segmentX, trackY, segmentWidth, 2, 0xFF000000 | PALETTE[index]);
        }
        int targetX = selected < 0 ? trackWidth / 2 : selected * segmentWidth + segmentWidth / 2;
        targetX = Math.min(trackWidth - 1, targetX);
        float target = targetX / (float) Math.max(1, trackWidth - 1);
        int handleX = trackX + Math.round(
                paletteAnimation.update(target, renderer.animationsEnabled()) * (trackWidth - 1));
        renderer.rect(handleX - 1, trackY - 2, 3, 6, ClickGuiTheme.TEXT);
        renderer.rect(handleX, trackY - 1, 1, 4, 0xFF000000 | rgb);
        if (expanded) {
            renderer.rect(handleX - 2, trackY + 5, 5, 1, ClickGuiTheme.PANEL_INSET);
            renderer.rect(handleX - 1, trackY + 4, 3, 1, ClickGuiTheme.PANEL_INSET);
            renderer.rect(handleX, trackY + 3, 1, 1, ClickGuiTheme.PANEL_INSET);
        }
    }

    private void renderChannel(ClickGuiRenderer renderer, String name, int channel, float[] hsb,
            float position) {
        int rowY = y + COLLAPSED_HEIGHT + channel * CHANNEL_HEIGHT;
        int trackX = x + 5;
        int trackY = rowY + 17;
        int trackWidth = Math.max(1, width - 10);
        renderer.verticallyCenteredText(name, trackX, rowY, 14, 8.0F, ClickGuiTheme.MUTED_TEXT);
        if (channel == 0) {
            int segmentWidth = Math.max(1, trackWidth / 6);
            for (int index = 0; index < 6; index++) {
                int segmentX = trackX + index * segmentWidth;
                int widthForSegment = index == 5 ? trackX + trackWidth - segmentX : segmentWidth;
                int left = 0xFF000000 | Color.HSBtoRGB(index / 6.0F, 1.0F, 1.0F) & 0xFFFFFF;
                int right = 0xFF000000 | Color.HSBtoRGB((index + 1) / 6.0F, 1.0F, 1.0F) & 0xFFFFFF;
                renderer.horizontalGradient(segmentX, trackY, widthForSegment, 2, left, right);
            }
        } else {
            int left = channel == 1 ? Color.HSBtoRGB(hsb[0], 0.0F, hsb[2])
                    : Color.HSBtoRGB(hsb[0], hsb[1], 0.0F);
            int right = channel == 1 ? Color.HSBtoRGB(hsb[0], 1.0F, hsb[2])
                    : Color.HSBtoRGB(hsb[0], hsb[1], 1.0F);
            renderer.horizontalGradient(trackX, trackY, trackWidth, 2, left, right);
        }
        float animatedPosition =
                channelAnimations[channel].update(position, renderer.animationsEnabled());
        int handleX = trackX + Math.round(animatedPosition * (trackWidth - 1));
        renderer.rect(handleX - 1, trackY - 2, 3, 6, ClickGuiTheme.TEXT);
        renderer.rect(handleX, trackY - 1, 1, 4, 0xFF000000 | value.getAsInt() & 0xFFFFFF);
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseY < y + 14 && mouseX >= x + 4 && mouseX < expandControlX + 8) {
            expanded = !expanded;
            height = preferredHeight();
            return true;
        }
        if (mouseY >= y + 13 && mouseY < y + COLLAPSED_HEIGHT) {
            selectPalette(mouseX);
            draggingPalette = true;
            controller.capture(this);
            return true;
        }
        if (expanded) {
            draggedChannel =
                    Math.max(0, Math.min(2, (mouseY - y - COLLAPSED_HEIGHT) / CHANNEL_HEIGHT));
            updateChannel(mouseX);
            controller.capture(this);
            return true;
        }
        return true;
    }

    @Override
    public void mouseDragged(ClickGuiController controller, int mouseX, int mouseY, int button) {
        if (draggingPalette) {
            selectPalette(mouseX);
        } else if (draggedChannel >= 0) {
            updateChannel(mouseX);
        }
    }

    @Override
    public void mouseReleased(ClickGuiController controller, int mouseX, int mouseY, int button) {
        draggingPalette = false;
        draggedChannel = -1;
        controller.releaseCapture(this);
    }

    private void selectPalette(int mouseX) {
        int segmentWidth = Math.max(1, (width - 10) / PALETTE.length);
        int trackWidth = segmentWidth * PALETTE.length;
        int trackX = x + (width - trackWidth) / 2;
        int index = Math.max(0, Math.min(PALETTE.length - 1, (mouseX - trackX) / segmentWidth));
        setter.accept(PALETTE[index]);
    }

    private void updateChannel(int mouseX) {
        int rgb = value.getAsInt() & 0xFFFFFF;
        float[] hsb = Color.RGBtoHSB(rgb >>> 16 & 0xFF, rgb >>> 8 & 0xFF, rgb & 0xFF, null);
        float amount =
                Math.max(0.0F, Math.min(1.0F, (mouseX - x - 5) / (float) Math.max(1, width - 11)));
        hsb[draggedChannel] = amount;
        setter.accept(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF);
    }

    private static int matchingPaletteIndex(int rgb) {
        for (int index = 0; index < PALETTE.length; index++) {
            if (rgb == PALETTE[index]) {
                return index;
            }
        }
        return -1;
    }
}
