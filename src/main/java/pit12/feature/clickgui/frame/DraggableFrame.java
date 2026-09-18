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
package pit12.feature.clickgui.frame;

import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.ClickGuiState;
import pit12.feature.clickgui.component.GuiComponent;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;

public abstract class DraggableFrame extends GuiComponent {
    public static final int HEADER_HEIGHT = 18;
    private static final int FRAME_RADIUS = 4;
    private static final int CONTENT_BOTTOM_INSET = FRAME_RADIUS;
    private final String frameId;
    private final String title;
    private final ClickGuiState state;
    private boolean dragging;
    private boolean collapsed;
    private int expandedHeight;
    private int dragOffsetX;
    private int dragOffsetY;

    protected DraggableFrame(String frameId, String title, ClickGuiState state, int defaultX,
            int defaultY, int width, int height) {
        super(0, 0, width, height);
        this.frameId = frameId;
        this.title = title;
        this.state = state;
        ClickGuiState.Position position = state.position(frameId, defaultX, defaultY);
        x = position.x();
        y = position.y();
        expandedHeight = this.height + CONTENT_BOTTOM_INSET;
        this.height = expandedHeight;
    }

    public final String frameId() {
        return frameId;
    }

    public final int frameX() {
        return x;
    }

    public final int frameY() {
        return y;
    }

    public final int frameWidth() {
        return width;
    }

    public final int frameHeight() {
        return height;
    }

    public final void setFrameHeight(int height) {
        expandedHeight = Math.max(HEADER_HEIGHT, height) + CONTENT_BOTTOM_INSET;
        if (!collapsed) {
            this.height = expandedHeight;
        }
    }

    protected final int contentViewportHeight() {
        return Math.max(0, height - HEADER_HEIGHT - CONTENT_BOTTOM_INSET);
    }

    protected final int contentBottom() {
        return y + HEADER_HEIGHT + contentViewportHeight();
    }

    public final void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
        state.move(frameId, x, y);
    }

    @Override
    public final void render(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks) {
        renderer.roundedRect(x, y, width, height, FRAME_RADIUS, ClickGuiTheme.PANEL);
        renderer.roundedRect(x, y, width, HEADER_HEIGHT, FRAME_RADIUS, ClickGuiTheme.PANEL);
        renderer.rect(x, y + HEADER_HEIGHT - 2, width, 2, ClickGuiTheme.PANEL);
        if (showHeaderIdentity()) {
            renderer.verticallyCenteredText(renderer.ellipsize(title, Math.max(1, width - 26)),
                    x + 6, y, HEADER_HEIGHT, ClickGuiTheme.TEXT);
        }
        renderHeaderAction(renderer, mouseX, mouseY);
        if (showCollapseControl()) {
            renderer.centeredTexture(collapsed ? TextureIcon.EXPAND : TextureIcon.COLLAPSE,
                    x + width - 11, y, HEADER_HEIGHT, 6, 6, ClickGuiTheme.MUTED_TEXT);
        }
        if (!collapsed) {
            renderer.pushScissor(x, y + HEADER_HEIGHT, width, contentViewportHeight());
            try {
                renderContent(renderer, mouseX, mouseY, partialTicks);
            } finally {
                renderer.popScissor();
            }
        }
        renderer.roundedOutline(x - 0.5F, y - 0.5F, width + 1.0F, height + 1.0F,
                FRAME_RADIUS + 0.1F, 1.0F, ClickGuiTheme.BORDER);
    }

    protected void renderHeaderAction(ClickGuiRenderer renderer, int mouseX, int mouseY) {}

    protected boolean showCollapseControl() {
        return true;
    }

    protected boolean showHeaderIdentity() {
        return true;
    }

    protected boolean canCollapse() {
        return true;
    }

    protected abstract void renderContent(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks);

    protected abstract boolean contentMousePressed(ClickGuiController controller, int mouseX,
            int mouseY, int button);

    protected boolean contentMouseWheel(ClickGuiController controller, int mouseX, int mouseY,
            int delta) {
        return false;
    }

    @Override
    public final boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (canCollapse() && mouseY < y + HEADER_HEIGHT
                && (button == 1 || showCollapseControl() && collapseControlContains(mouseX))) {
            if (button == 0 || button == 1) {
                setCollapsed(!collapsed);
                return true;
            }
        }
        if (headerLeadingActionContains(mouseX, mouseY)
                && headerLeadingActionPressed(controller, mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseY < y + HEADER_HEIGHT && !headerActionContains(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            controller.capture(this);
            return true;
        }
        if (headerActionContains(mouseX, mouseY)
                && headerActionPressed(controller, mouseX, mouseY, button)) {
            return true;
        }
        return contentMousePressed(controller, mouseX, mouseY, button);
    }

    protected boolean headerActionContains(int mouseX, int mouseY) {
        return false;
    }

    protected boolean headerActionPressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        return false;
    }

    protected boolean headerLeadingActionContains(int mouseX, int mouseY) {
        return false;
    }

    protected boolean headerLeadingActionPressed(ClickGuiController controller, int mouseX,
            int mouseY, int button) {
        return false;
    }

    private boolean collapseControlContains(int mouseX) {
        return mouseX >= x + width - 17 && mouseX < x + width;
    }

    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        height = collapsed ? HEADER_HEIGHT : expandedHeight;
    }

    @Override
    public final void mouseDragged(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        } else {
            contentMouseDragged(controller, mouseX, mouseY, button);
        }
    }

    protected void contentMouseDragged(ClickGuiController controller, int mouseX, int mouseY,
            int button) {}

    @Override
    public final void mouseReleased(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (dragging) {
            dragging = false;
            clamp(controller.screenWidth(), controller.screenHeight());
            state.move(frameId, x, y);
            controller.releaseCapture(this);
        }
        contentMouseReleased(controller, mouseX, mouseY, button);
    }

    protected void contentMouseReleased(ClickGuiController controller, int mouseX, int mouseY,
            int button) {}

    @Override
    public final boolean mouseWheel(ClickGuiController controller, int mouseX, int mouseY,
            int delta) {
        return contains(mouseX, mouseY) && contentMouseWheel(controller, mouseX, mouseY, delta);
    }

    public final void clamp(int screenWidth, int screenHeight) {
        int grabWidth = 24;
        x = Math.max(-width + grabWidth, Math.min(screenWidth - grabWidth, x));
        y = Math.max(0, Math.min(Math.max(0, screenHeight - HEADER_HEIGHT), y));
        state.move(frameId, x, y);
    }
}
