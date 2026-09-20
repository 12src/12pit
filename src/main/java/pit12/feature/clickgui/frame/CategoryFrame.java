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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.ClickGuiState;
import pit12.feature.clickgui.component.OptionComponent;
import pit12.feature.clickgui.component.UiAnimation;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.FeatureConfig;

public final class CategoryFrame extends DraggableFrame {
    private static final int FEATURE_HEIGHT = 20;
    private static final int MAX_CONTENT_HEIGHT = 180;
    private final ClickGuiController controller;
    private final ConfigCategory category;
    private final List<FeatureConfig> features;
    private final Map<ConfigOption<?>, OptionComponent> optionComponents =
            new LinkedHashMap<ConfigOption<?>, OptionComponent>();
    private final Map<FeatureConfig, UiAnimation> featureAnimations =
            new LinkedHashMap<FeatureConfig, UiAnimation>();
    private String expandedFeatureId;
    private int scroll;

    public CategoryFrame(ClickGuiController controller, ClickGuiState state,
            ConfigCategory category, List<FeatureConfig> features, int defaultX, int defaultY) {
        super("category." + category.id(), category.displayName(), state, defaultX, defaultY, 110,
                HEADER_HEIGHT + Math.min(MAX_CONTENT_HEIGHT,
                        Math.max(FEATURE_HEIGHT, features.size() * FEATURE_HEIGHT)));
        this.controller = controller;
        this.category = category;
        this.features = new ArrayList<FeatureConfig>(features);
        for (FeatureConfig feature : this.features) {
            featureAnimations.put(feature, new UiAnimation());
            for (ConfigOption<?> option : feature.options()) {
                optionComponents.put(option, new OptionComponent(option));
            }
        }
        setVisible(false);
    }

    public ConfigCategory category() {
        return category;
    }

    void revealFeature(String featureId) {
        expandedFeatureId = featureId;
        int featureOffset = 0;
        for (FeatureConfig feature : features) {
            if (feature.id().equals(featureId)) {
                break;
            }
            featureOffset += FEATURE_HEIGHT;
        }
        scroll = featureOffset;
        resizeToContent();
        clampScroll();
    }

    List<FeatureConfig> features() {
        return features;
    }

    @Override
    protected void renderContent(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks) {
        int rowY = frameY() + HEADER_HEIGHT - scroll;
        for (FeatureConfig feature : features) {
            boolean expanded = feature.id().equals(expandedFeatureId);
            if (rowY + FEATURE_HEIGHT > frameY() + HEADER_HEIGHT && rowY < contentBottom()) {
                renderFeature(renderer, feature, rowY, mouseX, mouseY, expanded);
            }
            rowY += FEATURE_HEIGHT;
            if (!expanded) {
                continue;
            }
            for (ConfigOption<?> option : feature.options()) {
                int optionHeight = optionHeight(option);
                if (rowY + optionHeight > frameY() + HEADER_HEIGHT && rowY < contentBottom()) {
                    renderOption(renderer, option, rowY, mouseX, mouseY, partialTicks);
                }
                rowY += optionHeight;
            }
        }
        int viewport = contentViewportHeight();
        renderer.scrollbar(frameX() + frameWidth() - 2, frameY() + HEADER_HEIGHT + 2, viewport - 4,
                viewport, contentHeight(), scroll);
    }

    private void renderFeature(ClickGuiRenderer renderer, FeatureConfig feature, int rowY,
            int mouseX, int mouseY, boolean expanded) {
        boolean hovered = inRow(mouseX, mouseY, rowY, FEATURE_HEIGHT);
        boolean enabled = feature.enabled();
        float progress =
                featureAnimations.get(feature).update(enabled, renderer.animationsEnabled());
        int background = expanded ? ClickGuiTheme.ROW_PRESSED
                : hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.ROW;
        renderer.rect(frameX(), rowY, frameWidth(), FEATURE_HEIGHT, background);
        int settingsX = frameX() + frameWidth() - 15;
        int iconColor = feature.options().isEmpty() ? ClickGuiTheme.DISABLED_TEXT
                : ClickGuiTheme.MUTED_TEXT;
        renderer.centeredTexture(TextureIcon.SETTINGS, settingsX + 5, rowY, FEATURE_HEIGHT, 6, 6,
                iconColor);
        int textRight = settingsX - 2;
        renderer.verticallyCenteredText(
                renderer.ellipsize(feature.displayName(), Math.max(1, textRight - frameX() - 7),
                        8.0F),
                frameX() + 6, rowY, FEATURE_HEIGHT, 8.0F,
                renderer.mix(ClickGuiTheme.MUTED_TEXT, ClickGuiTheme.ACCENT, progress));
        if (hovered && mouseX >= settingsX) {
            controller.tooltip(
                    feature.options().isEmpty() ? "No additional settings" : "Open settings",
                    mouseX, mouseY);
        } else if (hovered && !feature.description().isEmpty()) {
            controller.tooltip(feature.description(), mouseX, mouseY);
        }
    }

    private void renderOption(ClickGuiRenderer renderer, ConfigOption<?> option, int rowY,
            int mouseX, int mouseY, float partialTicks) {
        OptionComponent component = optionComponents.get(option);
        int optionHeight = component.preferredHeight();
        boolean hovered = inRow(mouseX, mouseY, rowY, optionHeight);
        renderer.rect(frameX(), rowY, frameWidth(), optionHeight,
                hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.PANEL);
        component.setBounds(frameX() + 7, rowY, frameWidth() - 14);
        component.render(renderer, mouseX, mouseY, partialTicks);
        if (hovered && !option.setting().description().isEmpty()) {
            controller.tooltip(option.setting().description(), mouseX, mouseY);
        }
    }

    @Override
    protected boolean contentMousePressed(ClickGuiController ignoredController, int mouseX,
            int mouseY, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        int rowY = frameY() + HEADER_HEIGHT - scroll;
        for (FeatureConfig feature : features) {
            if (inRow(mouseX, mouseY, rowY, FEATURE_HEIGHT)) {
                controller.focus(null);
                int settingsX = frameX() + frameWidth() - 15;
                if ((button == 1 || mouseX >= settingsX) && !feature.options().isEmpty()) {
                    expandedFeatureId =
                            feature.id().equals(expandedFeatureId) ? null : feature.id();
                } else if (button == 0 && feature.toggleable()) {
                    feature.setEnabled(!feature.enabled());
                }
                resizeToContent();
                clampScroll();
                return true;
            }
            rowY += FEATURE_HEIGHT;
            if (!feature.id().equals(expandedFeatureId)) {
                continue;
            }
            for (ConfigOption<?> option : feature.options()) {
                int optionHeight = optionHeight(option);
                if (inRow(mouseX, mouseY, rowY, optionHeight)) {
                    optionComponents.get(option).mousePressed(controller, mouseX, mouseY, button);
                    resizeToContent();
                    clampScroll();
                    return true;
                }
                rowY += optionHeight;
            }
        }
        return false;
    }

    @Override
    protected boolean contentMouseWheel(ClickGuiController controller, int mouseX, int mouseY,
            int delta) {
        if (contentHeight() <= contentViewportHeight()) {
            return false;
        }
        scroll -= Integer.signum(delta) * 18;
        clampScroll();
        return true;
    }

    private int contentHeight() {
        int result = features.size() * FEATURE_HEIGHT;
        for (FeatureConfig feature : features) {
            if (feature.id().equals(expandedFeatureId)) {
                for (ConfigOption<?> option : feature.options()) {
                    result += optionHeight(option);
                }
            }
        }
        return result;
    }

    private void clampScroll() {
        scroll = Math.max(0,
                Math.min(scroll, Math.max(0, contentHeight() - contentViewportHeight())));
    }

    private void resizeToContent() {
        setFrameHeight(HEADER_HEIGHT
                + Math.min(MAX_CONTENT_HEIGHT, Math.max(FEATURE_HEIGHT, contentHeight())));
    }

    private int optionHeight(ConfigOption<?> option) {
        return optionComponents.get(option).preferredHeight();
    }

    private boolean inRow(int mouseX, int mouseY, int rowY, int rowHeight) {
        return mouseX >= frameX() && mouseX < frameX() + frameWidth() && mouseY >= rowY
                && mouseY < rowY + rowHeight && mouseY >= frameY() + HEADER_HEIGHT
                && mouseY < contentBottom();
    }
}
