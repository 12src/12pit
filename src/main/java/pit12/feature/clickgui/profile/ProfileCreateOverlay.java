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
package pit12.feature.clickgui.profile;

import pit12.feature.clickgui.ClickGuiController;
import pit12.feature.clickgui.component.ButtonComponent;
import pit12.feature.clickgui.component.ContainerComponent;
import pit12.feature.clickgui.component.TextInputComponent;
import pit12.feature.clickgui.frame.DraggableFrame;
import pit12.feature.clickgui.frame.ProfilesFrame;
import pit12.feature.clickgui.render.ClickGuiRenderer;
import pit12.feature.clickgui.render.ClickGuiTheme;
import pit12.feature.profile.api.ProfileCreateSession;
import pit12.feature.profile.api.ProfileMutationResult;

public final class ProfileCreateOverlay extends ContainerComponent {
    private final ClickGuiController controller;
    private final ProfilesFrame owner;
    private final ProfileCreateSession create;
    private final TextInputComponent nameInput;
    private final ButtonComponent cancelButton;
    private final ButtonComponent createButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public ProfileCreateOverlay(ClickGuiController controller, ProfilesFrame owner,
            ProfileCreateSession create) {
        super(0, 0, controller.screenWidth(), controller.screenHeight());
        this.controller = controller;
        this.owner = owner;
        this.create = create;
        nameInput = new TextInputComponent(0, 0, 0, 0, "Profile name", this::submit);
        nameInput.setMaximumLength(48);
        cancelButton = new ButtonComponent(0, 0, 0, 0, "Cancel", this::cancel);
        createButton = new ButtonComponent(0, 0, 0, 0, "Create", this::submit);
        createButton.setAccentColors();
        add(nameInput);
        add(cancelButton);
        add(createButton);
    }

    @Override
    public pit12.feature.clickgui.component.GuiComponent initialFocus() {
        return nameInput;
    }

    @Override
    public void render(ClickGuiRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        setBounds(0, 0, controller.screenWidth(), controller.screenHeight());
        panelWidth = Math.max(146, owner.frameWidth() - 8);
        panelHeight = 88;
        panelX = owner.frameX() + (owner.frameWidth() - panelWidth) / 2;
        panelY = owner.frameY() + DraggableFrame.HEADER_HEIGHT + 8;
        panelY = Math.min(panelY, controller.screenHeight() - panelHeight - 4);
        renderer.rect(owner.frameX(), owner.frameY() + DraggableFrame.HEADER_HEIGHT,
                owner.frameWidth(), owner.frameHeight() - DraggableFrame.HEADER_HEIGHT,
                ClickGuiTheme.OVERLAY_DIM);
        renderer.roundedRect(panelX, panelY, panelWidth, panelHeight, 4, ClickGuiTheme.PANEL);
        renderer.roundedOutline(panelX - 0.5F, panelY - 0.5F, panelWidth + 1.0F, panelHeight + 1.0F,
                4.1F, 1.0F, ClickGuiTheme.BORDER);
        renderer.verticallyCenteredText("New profile", panelX + 8, panelY, 20, ClickGuiTheme.TEXT);
        renderer.rect(panelX + 8, panelY + 19, panelWidth - 16, 1, ClickGuiTheme.DIVIDER);
        nameInput.setBounds(panelX + 8, panelY + 24, panelWidth - 16, 20);
        int buttonY = panelY + panelHeight - 27;
        cancelButton.setBounds(panelX + 8, buttonY, 54, 20);
        createButton.setBounds(panelX + panelWidth - 62, buttonY, 54, 20);
        super.render(renderer, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mousePressed(ClickGuiController controller, int mouseX, int mouseY, int button) {
        super.mousePressed(controller, mouseX, mouseY, button);
        return true;
    }

    @Override
    public void close() {
        if (!create.isClosed()) {
            create.cancel();
        }
        super.close();
    }

    private void submit() {
        create.setName(nameInput.text());
        ProfileMutationResult result = create.commit();
        if (result.succeeded()) {
            controller.closeModal(this);
        }
    }

    private void cancel() {
        create.cancel();
        controller.closeModal(this);
    }
}
