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
package pit12.feature.profile.api;

public final class ProfileMutationResult {
    public enum Status {
        SUCCESS,
        LOADING,
        NOT_FOUND,
        INVALID_NAME,
        DUPLICATE_NAME,
        ACTIVE_PROFILE_PROTECTED,
        STORAGE_UNAVAILABLE,
        INVALID_VALUE,
        CLOSED_SESSION
    }

    private static final ProfileMutationResult SUCCESS =
            new ProfileMutationResult(Status.SUCCESS, "", null);
    private final Status status;
    private final String message;
    private final ProfileCreateSession createSession;

    private ProfileMutationResult(Status status, String message,
            ProfileCreateSession createSession) {
        this.status = status;
        this.message = message;
        this.createSession = createSession;
    }

    public static ProfileMutationResult success() {
        return SUCCESS;
    }

    public static ProfileMutationResult create(ProfileCreateSession createSession) {
        if (createSession == null) {
            throw new NullPointerException("createSession");
        }
        return new ProfileMutationResult(Status.SUCCESS, "", createSession);
    }

    public static ProfileMutationResult failure(Status status, String message) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("A failure result cannot use SUCCESS");
        }
        return new ProfileMutationResult(status, message == null ? "" : message, null);
    }

    public Status status() {
        return status;
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public String message() {
        return message;
    }

    public ProfileCreateSession createSession() {
        return createSession;
    }
}
