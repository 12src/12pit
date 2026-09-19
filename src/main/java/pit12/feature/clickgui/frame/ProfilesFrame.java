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
import java.util.UUID;
import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.ClickGuiState;
import pit12.feature.clickgui.component.TextInputComponent;
import pit12.feature.clickgui.profile.ProfileCreateOverlay;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiRenderer.TextureIcon;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.ProfileSummary;
import pit12.feature.profile.api.Profiles;
import pit12.feature.profile.api.ProfilesSnapshot;
import pit12.feature.profile.api.ProfilesSnapshot.LoadState;

public final class ProfilesFrame extends DraggableFrame {
    private static final int CREATE_HEIGHT = 22;
    private static final int ROW_HEIGHT = 20;
    private final ClickGuiController controller;
    private final Profiles profiles;
    private final List<RowView> rows = new ArrayList<RowView>();
    private final TextInputComponent renameInput;
    private long consumedRevision = Long.MIN_VALUE;
    private int scroll;
    private UUID renamingProfileId;

    public ProfilesFrame(ClickGuiController controller, ClickGuiState state, Profiles profiles) {
        super("profiles", "Profiles", state, 148, 32, 110, 191);
        this.controller = controller;
        this.profiles = profiles;
        renameInput = new TextInputComponent(0, 0, 0, 0, "Profile name", this::submitRename);
        renameInput.setMaximumLength(48);
        setVisible(false);
    }

    @Override
    protected void renderContent(ClickGuiRenderer renderer, int mouseX, int mouseY,
            float partialTicks) {
        if (renamingProfileId != null && !renameInput.isFocused()) {
            finishRename();
        }
        ProfilesSnapshot snapshot = profiles.snapshot();
        if (snapshot.revision() != consumedRevision) {
            rebuildRows(snapshot);
        }
        int contentTop = frameY() + HEADER_HEIGHT;
        int rowY = contentTop - scroll;
        if (snapshot.loadState() == LoadState.LOADING) {
            renderer.centeredText("Loading profiles...", frameX(), contentTop, frameWidth(), 32,
                    ClickGuiTheme.MUTED_TEXT);
            return;
        }
        boolean createHovered = inContentRow(mouseX, mouseY, rowY, CREATE_HEIGHT);
        renderer.rect(frameX(), rowY, frameWidth(), CREATE_HEIGHT,
                createHovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.ROW);
        renderer.centeredTexture(TextureIcon.ADD, frameX() + 7, rowY, CREATE_HEIGHT, 7, 7,
                ClickGuiTheme.ACCENT);
        renderer.verticallyCenteredText("Create new", frameX() + 21, rowY, CREATE_HEIGHT,
                ClickGuiTheme.TEXT);
        rowY += CREATE_HEIGHT;
        renderer.rect(frameX() + 6, rowY, frameWidth() - 12, 1, ClickGuiTheme.DIVIDER);
        rowY += 3;
        for (int index = 0; index < rows.size(); index++) {
            int renderY = rowY + index * ROW_HEIGHT;
            if (renderY + ROW_HEIGHT <= contentTop || renderY >= contentBottom()) {
                continue;
            }
            renderRow(renderer, snapshot, rows.get(index), renderY, mouseX, mouseY, partialTicks);
        }
        if (rows.isEmpty()) {
            renderer.centeredText("No profiles", frameX(), rowY + 8, frameWidth(), 24,
                    ClickGuiTheme.MUTED_TEXT);
        }
        int viewport = contentViewportHeight();
        renderer.scrollbar(frameX() + frameWidth() - 2, contentTop + 2, viewport - 4, viewport,
                profileContentHeight(), scroll);
    }

    private void renderRow(ClickGuiRenderer renderer, ProfilesSnapshot snapshot, RowView row,
            int rowY, int mouseX, int mouseY, float partialTicks) {
        ProfileSummary profile = row.profile;
        boolean active = profile.id().equals(snapshot.activeProfileId());
        boolean hovered = inContentRow(mouseX, mouseY, rowY, ROW_HEIGHT);
        boolean renaming = profile.id().equals(renamingProfileId);
        int background = active ? ClickGuiTheme.ACCENT_DARK
                : hovered ? ClickGuiTheme.ROW_HOVER : ClickGuiTheme.ROW;
        renderer.rect(frameX(), rowY, frameWidth(), ROW_HEIGHT, background);
        if (renaming) {
            renameInput.setBounds(frameX() + 4, rowY + 2, frameWidth() - 41, ROW_HEIGHT - 4);
            renameInput.render(renderer, mouseX, mouseY, partialTicks);
        } else {
            if (row.clippedName == null) {
                row.clippedName = renderer.ellipsize(profile.name(), frameWidth() - 48);
            }
            renderer.verticallyCenteredText(row.clippedName, frameX() + 8, rowY, ROW_HEIGHT,
                    active ? ClickGuiTheme.ACCENT_TEXT : ClickGuiTheme.MUTED_TEXT);
        }
        if (!hovered && !renaming) {
            return;
        }
        int editX = frameX() + frameWidth() - 32;
        int deleteX = frameX() + frameWidth() - 16;
        renderer.centeredTexture(TextureIcon.EDIT, editX, rowY, ROW_HEIGHT, 7, 6,
                active ? ClickGuiTheme.ACCENT_TEXT
                        : renaming ? ClickGuiTheme.ACCENT : ClickGuiTheme.TEXT);
        renderer.centeredTexture(TextureIcon.DELETE, deleteX, rowY, ROW_HEIGHT, 6, 6,
                active ? ClickGuiTheme.DISABLED_TEXT : ClickGuiTheme.DANGER);
        if (hovered && mouseX >= frameX() + frameWidth() - 36) {
            if (mouseX < frameX() + frameWidth() - 18) {
                controller.tooltip("Rename profile", mouseX, mouseY);
            } else if (!active) {
                controller.tooltip("Delete profile", mouseX, mouseY);
            }
        }
    }

    @Override
    protected boolean contentMousePressed(ClickGuiController controller, int mouseX, int mouseY,
            int button) {
        ProfilesSnapshot snapshot = profiles.snapshot();
        if (button != 0 || snapshot.loadState() == LoadState.LOADING) {
            return false;
        }
        if (renamingProfileId != null && renameInput.contains(mouseX, mouseY)) {
            return renameInput.mousePressed(controller, mouseX, mouseY, button);
        }
        if (renamingProfileId != null && !finishRename()) {
            return true;
        }
        int rowY = frameY() + HEADER_HEIGHT - scroll;
        if (inContentRow(mouseX, mouseY, rowY, CREATE_HEIGHT)) {
            ProfileMutationResult result = profiles.beginCreate();
            if (result.succeeded()) {
                controller.openModal(
                        new ProfileCreateOverlay(controller, this, result.createSession()));
            }
            return true;
        }
        rowY += CREATE_HEIGHT + 3;
        int index = (mouseY - rowY) / ROW_HEIGHT;
        if (mouseY < rowY || index < 0 || index >= rows.size()) {
            return false;
        }
        ProfileSummary profile = rows.get(index).profile;
        if (mouseX >= frameX() + frameWidth() - 18) {
            if (!profile.id().equals(snapshot.activeProfileId())) {
                profiles.delete(profile.id());
            }
        } else if (mouseX >= frameX() + frameWidth() - 36) {
            startRename(profile, rowY + index * ROW_HEIGHT);
        } else {
            profiles.switchTo(profile.id());
        }
        return true;
    }

    @Override
    protected boolean contentMouseWheel(ClickGuiController controller, int mouseX, int mouseY,
            int delta) {
        int contentHeight = profileContentHeight();
        int viewport = contentViewportHeight();
        if (contentHeight <= viewport) {
            return false;
        }
        scroll = Math.max(0,
                Math.min(contentHeight - viewport, scroll - Integer.signum(delta) * ROW_HEIGHT));
        return true;
    }

    private void startRename(ProfileSummary profile, int rowY) {
        renamingProfileId = profile.id();
        renameInput.setText(profile.name());
        renameInput.setBounds(frameX() + 4, rowY + 2, frameWidth() - 41, ROW_HEIGHT - 4);
        controller.focus(renameInput);
    }

    private boolean finishRename() {
        if (renamingProfileId == null) {
            return true;
        }
        ProfileMutationResult result = profiles.rename(renamingProfileId, renameInput.text());
        if (!result.succeeded()) {
            controller.focus(renameInput);
            return false;
        }
        renamingProfileId = null;
        controller.focus(null);
        return true;
    }

    private void submitRename() {
        finishRename();
    }

    private void rebuildRows(ProfilesSnapshot snapshot) {
        rows.clear();
        boolean renameTargetExists = renamingProfileId == null;
        for (ProfileSummary profile : snapshot.profiles()) {
            rows.add(new RowView(profile));
            renameTargetExists |= profile.id().equals(renamingProfileId);
        }
        if (!renameTargetExists) {
            renamingProfileId = null;
            controller.focus(null);
        }
        consumedRevision = snapshot.revision();
        int maximum = Math.max(0, profileContentHeight() - contentViewportHeight());
        scroll = Math.min(scroll, maximum);
    }

    private int profileContentHeight() {
        return rows.size() * ROW_HEIGHT + CREATE_HEIGHT + 3;
    }

    private boolean inContentRow(int mouseX, int mouseY, int rowY, int rowHeight) {
        return mouseX >= frameX() && mouseX < frameX() + frameWidth() && mouseY >= rowY
                && mouseY < rowY + rowHeight && mouseY >= frameY() + HEADER_HEIGHT
                && mouseY < contentBottom();
    }

    private static final class RowView {
        private final ProfileSummary profile;
        private String clippedName;

        private RowView(ProfileSummary profile) {
            this.profile = profile;
        }
    }
}
