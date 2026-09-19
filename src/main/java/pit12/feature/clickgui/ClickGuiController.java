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
package pit12.feature.clickgui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import pit12.feature.clickgui.component.GuiComponent;
import pit12.feature.clickgui.frame.CategoryFrame;
import pit12.feature.clickgui.frame.ClickGuiSettingsFrame;
import pit12.feature.clickgui.frame.DraggableFrame;
import pit12.feature.clickgui.frame.ProfilesFrame;
import pit12.feature.clickgui.frame.RootTreeFrame;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.feature.hudeditor.api.HudEditor;
import pit12.feature.profile.api.Profiles;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;

public final class ClickGuiController {
    private final ClickGuiState state = new ClickGuiState();
    private final ClickGuiRenderer renderer;
    private final ClickGuiConfig config;
    private final HudEditor hudEditor;
    private final RootTreeFrame rootFrame;
    private final ClickGuiSettingsFrame settingsFrame;
    private final List<DraggableFrame> orderedFrames = new ArrayList<DraggableFrame>();
    private final List<GuiComponent> modalStack = new ArrayList<GuiComponent>();
    private GuiComponent focusedComponent;
    private GuiComponent mouseCapture;
    private int screenWidth;
    private int screenHeight;
    private String tooltip;
    private int tooltipX;
    private int tooltipY;

    public ClickGuiController(ConfigCatalog catalog, Profiles profiles, ClickGuiConfig config,
            HudEditor hudEditor) {
        this.config = config;
        this.hudEditor = hudEditor;
        renderer = new ClickGuiRenderer(Minecraft.getMinecraft(), config);
        rootFrame = new RootTreeFrame(this, state);
        orderedFrames.add(rootFrame);
        Map<ConfigCategory, List<FeatureConfig>> grouped =
                new LinkedHashMap<ConfigCategory, List<FeatureConfig>>();
        for (FeatureConfig feature : catalog.features()) {
            if (feature == config) {
                continue;
            }
            List<FeatureConfig> categoryFeatures = grouped.get(feature.category());
            if (categoryFeatures == null) {
                categoryFeatures = new ArrayList<FeatureConfig>();
                grouped.put(feature.category(), categoryFeatures);
            }
            categoryFeatures.add(feature);
        }
        ArrayList<ConfigCategory> categories = new ArrayList<ConfigCategory>(grouped.keySet());
        Collections.sort(categories);
        for (int index = 0; index < categories.size(); index++) {
            ConfigCategory category = categories.get(index);
            CategoryFrame frame = new CategoryFrame(this, state, category, grouped.get(category),
                    148 + index * 14, 32 + index * 12);
            rootFrame.addCategory(frame);
            orderedFrames.add(frame);
        }
        ProfilesFrame profilesFrame = new ProfilesFrame(this, state, profiles);
        rootFrame.setProfilesFrame(profilesFrame);
        orderedFrames.add(profilesFrame);
        settingsFrame = new ClickGuiSettingsFrame(this, state, config);
        orderedFrames.add(settingsFrame);
    }

    public void resize(int screenWidth, int screenHeight, float interfaceScale, float pixelScale) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        renderer.resize(screenWidth, screenHeight, interfaceScale, pixelScale);
        for (DraggableFrame frame : orderedFrames) {
            frame.clamp(screenWidth, screenHeight);
        }
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        tooltip = null;
        renderer.begin();
        try {
            if (config.dimBackground().get().booleanValue()) {
                renderer.rect(0, 0, screenWidth, screenHeight, ClickGuiTheme.SCREEN_DIM);
            }
            for (DraggableFrame frame : orderedFrames) {
                if (frame.isVisible()) {
                    frame.render(renderer, mouseX, mouseY, partialTicks);
                }
            }
            renderHudEditorButton(mouseX, mouseY);
            if (!modalStack.isEmpty()) {
                tooltip = null;
                modalStack.get(modalStack.size() - 1).render(renderer, mouseX, mouseY,
                        partialTicks);
            }
            if (tooltip != null) {
                renderer.tooltip(tooltip, tooltipX, tooltipY);
            }
        } finally {
            renderer.end();
        }
    }

    public void mousePressed(int mouseX, int mouseY, int button) {
        if (focusedComponent != null && !focusedComponent.contains(mouseX, mouseY)) {
            focus(null);
        }
        if (!modalStack.isEmpty()) {
            modalStack.get(modalStack.size() - 1).mousePressed(this, mouseX, mouseY, button);
            return;
        }
        if (hudEditorButtonContains(mouseX, mouseY)) {
            if (button == 0) {
                hudEditor.open();
            }
            return;
        }
        for (int index = orderedFrames.size() - 1; index >= 0; index--) {
            DraggableFrame frame = orderedFrames.get(index);
            if (!frame.isVisible() || !frame.contains(mouseX, mouseY)) {
                continue;
            }
            bringToFront(frame);
            if (frame.mousePressed(this, mouseX, mouseY, button)) {
                return;
            }
            break;
        }
        focus(null);
    }

    public void mouseDragged(int mouseX, int mouseY, int button) {
        if (mouseCapture != null) {
            mouseCapture.mouseDragged(this, mouseX, mouseY, button);
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        GuiComponent captured = mouseCapture;
        if (captured != null) {
            captured.mouseReleased(this, mouseX, mouseY, button);
            releaseCapture(captured);
        }
    }

    public void mouseWheel(int mouseX, int mouseY, int delta) {
        if (delta == 0) {
            return;
        }
        if (!modalStack.isEmpty()) {
            modalStack.get(modalStack.size() - 1).mouseWheel(this, mouseX, mouseY, delta);
            return;
        }
        for (int index = orderedFrames.size() - 1; index >= 0; index--) {
            DraggableFrame frame = orderedFrames.get(index);
            if (frame.isVisible() && frame.contains(mouseX, mouseY)) {
                frame.mouseWheel(this, mouseX, mouseY, delta);
                return;
            }
        }
    }

    public boolean keyTyped(char character, int keyCode) {
        if (focusedComponent != null && focusedComponent.keyTyped(this, character, keyCode)) {
            return true;
        }
        return !modalStack.isEmpty()
                && modalStack.get(modalStack.size() - 1).keyTyped(this, character, keyCode);
    }

    public boolean hasFocusedInput() {
        return focusedComponent != null;
    }

    public void capture(GuiComponent component) {
        mouseCapture = component;
    }

    public void releaseCapture(GuiComponent component) {
        if (mouseCapture == component) {
            mouseCapture = null;
        }
    }

    public void focus(GuiComponent component) {
        if (focusedComponent == component) {
            return;
        }
        if (focusedComponent != null) {
            focusedComponent.focusChanged(false);
        }
        focusedComponent = component;
        if (focusedComponent != null) {
            focusedComponent.focusChanged(true);
        }
    }

    public void openModal(GuiComponent modal) {
        focus(null);
        mouseCapture = null;
        modalStack.add(modal);
        focus(modal.initialFocus());
    }

    public void closeModal(GuiComponent modal) {
        int index = modalStack.lastIndexOf(modal);
        if (index < 0) {
            return;
        }
        for (int current = modalStack.size() - 1; current >= index; current--) {
            modalStack.remove(current).close();
        }
        focus(null);
        mouseCapture = null;
    }

    public void toggleFrame(DraggableFrame frame) {
        frame.setVisible(!frame.isVisible());
        if (frame.isVisible()) {
            frame.clamp(screenWidth, screenHeight);
            bringToFront(frame);
        }
    }

    public void showFrame(DraggableFrame frame) {
        frame.setVisible(true);
        frame.clamp(screenWidth, screenHeight);
        bringToFront(frame);
    }

    public void openSettings() {
        settingsFrame.moveTo(rootFrame.frameX(), rootFrame.frameY());
        rootFrame.setVisible(false);
        settingsFrame.setVisible(true);
        bringToFront(settingsFrame);
    }

    public void closeSettings() {
        rootFrame.moveTo(settingsFrame.frameX(), settingsFrame.frameY());
        settingsFrame.setVisible(false);
        rootFrame.setVisible(true);
        bringToFront(rootFrame);
    }

    public void tooltip(String text, int mouseX, int mouseY) {
        if (!config.showTooltips().get().booleanValue()) {
            return;
        }
        tooltip = text;
        tooltipX = mouseX;
        tooltipY = mouseY;
    }

    public void close() {
        for (int index = modalStack.size() - 1; index >= 0; index--) {
            modalStack.get(index).close();
        }
        modalStack.clear();
        focus(null);
        mouseCapture = null;
    }

    public void dispose() {
        close();
        renderer.close();
    }

    private void renderHudEditorButton(int mouseX, int mouseY) {
        int x = screenWidth - 26;
        int y = screenHeight - 26;
        boolean hovered = hudEditorButtonContains(mouseX, mouseY);
        renderer.centeredTexture(TextureIcon.HUD_EDITOR, x, y, 20, 20, 20,
                hovered ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED_TEXT);
    }

    private boolean hudEditorButtonContains(int mouseX, int mouseY) {
        return mouseX >= screenWidth - 26 && mouseX < screenWidth - 6 && mouseY >= screenHeight - 26
                && mouseY < screenHeight - 6;
    }

    private void bringToFront(DraggableFrame frame) {
        orderedFrames.remove(frame);
        orderedFrames.add(frame);
    }
}
