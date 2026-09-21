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
package pit12.feature.profile;

import java.util.UUID;
import pit12.feature.profile.api.ProfileCreateSession;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.ProfileMutationResult.Status;
import pit12.runtime.config.ConfigSnapshot;

final class ProfileCreateOperation implements ProfileCreateSession {
    private final ProfileController controller;
    private final UUID profileId;
    private final ConfigSnapshot config;
    private String name = "";
    private boolean closed;

    ProfileCreateOperation(ProfileController controller, UUID profileId, ConfigSnapshot config) {
        this.controller = controller;
        this.profileId = profileId;
        this.config = config;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setName(String name) {
        if (!closed) {
            this.name = name == null ? "" : name;
        }
    }

    @Override
    public ProfileMutationResult commit() {
        if (closed) {
            return closedResult();
        }
        ProfileMutationResult result = controller.commit(this);
        if (result.succeeded()) {
            closed = true;
        }
        return result;
    }

    @Override
    public void cancel() {
        closed = true;
    }

    UUID profileId() {
        return profileId;
    }

    String name() {
        return name;
    }

    ConfigSnapshot config() {
        return config;
    }

    private static ProfileMutationResult closedResult() {
        return ProfileMutationResult.failure(Status.CLOSED_SESSION,
                "This create session is already closed");
    }
}
