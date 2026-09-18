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
import java.util.List;
import java.util.Locale;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.ClickGuiState;
import pit12.feature.clickgui.component.TextInputComponent;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.runtime.config.FeatureConfig;

public final class RootTreeFrame extends DraggableFrame {
    private static final int ROW_HEIGHT = 20;
    private static final int PROFILE_SEPARATOR_HEIGHT = 7;
    private final ClickGuiController controller;
    private final List<CategoryFrame> categories = new ArrayList<CategoryFrame>();
    private final List<SearchResult> searchResults = new ArrayList<SearchResult>();
    private final TextInputComponent searchInput;
    private ProfilesFrame profilesFrame;
    private String previousQuery = "";
    private boolean searching;
    private int searchScroll;

    public RootTreeFrame(ClickGuiController controller, ClickGuiState state) {
        super("root", "12pit", state, 32, 32, 110,
                HEADER_HEIGHT + PROFILE_SEPARATOR_HEIGHT + ROW_HEIGHT);
        this.controller = controller;
        searchInput = new TextInputComponent(0, 0, 0, 0, "", () -> {
        });
        searchInput.setChromeVisible(false);
    }

    public void addCategory(CategoryFrame frame) {
        categories.add(frame);
        setFrameHeight(HEADER_HEIGHT + categories.size() * ROW_HEIGHT + PROFILE_SEPARATOR_HEIGHT
                + ROW_HEIGHT);
    }

    public void setProfilesFrame(ProfilesFrame profilesFrame) {
        this.profilesFrame = profilesFrame;
    }

    @Override
    protected boolean showHeaderIdentity() {
        return !searching;
    }

    @Override
    protected void renderContent(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks) {
        refreshSearch();
        if (searching && !previousQuery.isEmpty()) {
            renderSearchResults(renderer, mouseX, mouseY);
            return;
        }
        int rowY = frameY() + HEADER_HEIGHT;
        for (CategoryFrame categoryFrame : categories) {
            renderNavigationRow(renderer, categoryFrame.category().displayName(),
                    categoryFrame.isVisible(), rowY, mouseX, mouseY);
            rowY += ROW_HEIGHT;
        }
        renderer.rect(frameX() + 6, rowY + 3, frameWidth() - 12, 1, ClickGuiTheme.DIVIDER);
        rowY += PROFILE_SEPARATOR_HEIGHT;
        renderNavigationRow(renderer, "Profiles",
                profilesFrame != null && profilesFrame.isVisible(), rowY, mouseX, mouseY);
    }

    @Override
    protected void renderHeaderAction(ClickGuiRenderer renderer, int mouseX, int mouseY) {
        if (searching) {
            searchInput.setBounds(frameX(), frameY(), frameWidth() - 34, HEADER_HEIGHT);
            searchInput.render(renderer, mouseX, mouseY, 0.0F);
        }
        boolean headerHovered = mouseY >= frameY() && mouseY < frameY() + HEADER_HEIGHT;
        int headerIconY = frameY() + (HEADER_HEIGHT - 8) / 2;
        renderer.searchIcon(frameX() + frameWidth() - 28, headerIconY,
                searching ? ClickGuiTheme.ACCENT_DARK
                        : headerHovered && mouseX >= frameX() + frameWidth() - 34
                                && mouseX < frameX() + frameWidth() - 18 ? ClickGuiTheme.TEXT
                                        : ClickGuiTheme.MUTED_TEXT);
        renderer.settingsIcon(frameX() + frameWidth() - 13, headerIconY,
                headerHovered && mouseX >= frameX() + frameWidth() - 18 ? ClickGuiTheme.TEXT
                        : ClickGuiTheme.MUTED_TEXT);
    }

    @Override
    protected boolean showCollapseControl() {
        return false;
    }

    @Override
    protected boolean headerActionContains(int mouseX, int mouseY) {
        return mouseX >= frameX() + frameWidth() - 34 && mouseX < frameX() + frameWidth()
                && mouseY >= frameY() && mouseY < frameY() + HEADER_HEIGHT;
    }

    @Override
    protected boolean headerActionPressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        if (button != 0) {
            return false;
        }
        if (mouseX >= frameX() + frameWidth() - 18) {
            controller.openSettings();
        } else {
            searching = !searching;
            if (searching) {
                searchInput.setBounds(frameX(), frameY(), frameWidth() - 34, HEADER_HEIGHT);
                controller.focus(searchInput);
            } else {
                searchInput.setText("");
                previousQuery = "";
                searchScroll = 0;
                setFrameHeight(defaultHeight());
                controller.focus(null);
            }
        }
        return true;
    }

    @Override
    protected boolean headerLeadingActionContains(int mouseX, int mouseY) {
        return searching && searchInput.contains(mouseX, mouseY);
    }

    @Override
    protected boolean headerLeadingActionPressed(ClickGuiController controller, int mouseX,
            int mouseY, int button) {
        return searchInput.mousePressed(controller, mouseX, mouseY, button);
    }

    private void renderNavigationRow(ClickGuiRenderer renderer, String name, boolean selected,
            int rowY, int mouseX, int mouseY) {
        boolean hovered = mouseX >= frameX() && mouseX < frameX() + frameWidth() && mouseY >= rowY
                && mouseY < rowY + ROW_HEIGHT;
        renderer.rect(frameX(), rowY, frameWidth(), ROW_HEIGHT,
                selected || hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.ROW);
        int foreground = selected ? ClickGuiTheme.ACCENT_DARK
                : hovered ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED_TEXT;
        renderer.verticallyCenteredText(name, frameX() + 7, rowY, ROW_HEIGHT, foreground);
        renderer.centeredTexture(TextureIcon.RIGHT, frameX() + frameWidth() - 11, rowY, ROW_HEIGHT,
                5, 5, selected ? ClickGuiTheme.ACCENT_DARK : ClickGuiTheme.MUTED_TEXT);
    }

    @Override
    protected boolean contentMousePressed(ClickGuiController ignoredController, int mouseX,
            int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (searching && !previousQuery.isEmpty()) {
            int index = (mouseY - frameY() - HEADER_HEIGHT + searchScroll) / ROW_HEIGHT;
            if (mouseY < frameY() + HEADER_HEIGHT || index < 0 || index >= searchResults.size()) {
                return false;
            }
            SearchResult result = searchResults.get(index);
            if (!result.feature.options().isEmpty()) {
                result.category.revealFeature(result.feature.id());
                controller.showFrame(result.category);
            }
            return true;
        }
        int localY = mouseY - frameY() - HEADER_HEIGHT;
        int categoryArea = categories.size() * ROW_HEIGHT;
        if (localY >= 0 && localY < categoryArea) {
            controller.toggleFrame(categories.get(localY / ROW_HEIGHT));
            return true;
        }
        int profilesStart = categoryArea + PROFILE_SEPARATOR_HEIGHT;
        if (localY >= profilesStart && localY < profilesStart + ROW_HEIGHT
                && profilesFrame != null) {
            controller.toggleFrame(profilesFrame);
            return true;
        }
        return false;
    }

    @Override
    protected boolean contentMouseWheel(ClickGuiController controller, int mouseX, int mouseY,
            int delta) {
        if (!searching || previousQuery.isEmpty()) {
            return false;
        }
        int viewport = contentViewportHeight();
        int maximum = Math.max(0, searchResults.size() * ROW_HEIGHT - viewport);
        searchScroll =
                Math.max(0, Math.min(maximum, searchScroll - Integer.signum(delta) * ROW_HEIGHT));
        return maximum > 0;
    }

    private void renderSearchResults(ClickGuiRenderer renderer, int mouseX, int mouseY) {
        int rowY = frameY() + HEADER_HEIGHT - searchScroll;
        if (searchResults.isEmpty()) {
            renderer.centeredText("No modules", frameX(), rowY, frameWidth(), ROW_HEIGHT,
                    ClickGuiTheme.MUTED_TEXT);
            return;
        }
        for (SearchResult result : searchResults) {
            if (rowY + ROW_HEIGHT > frameY() + HEADER_HEIGHT && rowY < contentBottom()) {
                boolean hovered = mouseX >= frameX() && mouseX < frameX() + frameWidth()
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                renderer.rect(frameX(), rowY, frameWidth(), ROW_HEIGHT,
                        hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.ROW);
                renderer.verticallyCenteredText(
                        renderer.ellipsize(result.feature.displayName(), frameWidth() - 29),
                        frameX() + 6, rowY, ROW_HEIGHT, ClickGuiTheme.MUTED_TEXT);
                if (!result.feature.options().isEmpty()) {
                    renderer.centeredTexture(TextureIcon.SETTINGS, frameX() + frameWidth() - 12,
                            rowY, ROW_HEIGHT, 6, 6, ClickGuiTheme.MUTED_TEXT);
                }
            }
            rowY += ROW_HEIGHT;
        }
        int viewport = contentViewportHeight();
        renderer.scrollbar(frameX() + frameWidth() - 2, frameY() + HEADER_HEIGHT + 2, viewport - 4,
                viewport, searchResults.size() * ROW_HEIGHT, searchScroll);
    }

    private void refreshSearch() {
        String query = searching ? searchInput.text().trim().toLowerCase(Locale.ROOT) : "";
        if (query.equals(previousQuery)) {
            return;
        }
        previousQuery = query;
        searchResults.clear();
        searchScroll = 0;
        if (query.isEmpty()) {
            setFrameHeight(defaultHeight());
            return;
        }
        for (CategoryFrame category : categories) {
            for (FeatureConfig feature : category.features()) {
                if (feature.displayName().toLowerCase(Locale.ROOT).contains(query)) {
                    searchResults.add(new SearchResult(category, feature));
                }
            }
        }
        setFrameHeight(HEADER_HEIGHT
                + Math.min(180, Math.max(ROW_HEIGHT, searchResults.size() * ROW_HEIGHT)));
    }

    private int defaultHeight() {
        return HEADER_HEIGHT + categories.size() * ROW_HEIGHT + PROFILE_SEPARATOR_HEIGHT
                + ROW_HEIGHT;
    }

    private static final class SearchResult {
        private final CategoryFrame category;
        private final FeatureConfig feature;

        private SearchResult(CategoryFrame category, FeatureConfig feature) {
            this.category = category;
            this.feature = feature;
        }
    }
}
