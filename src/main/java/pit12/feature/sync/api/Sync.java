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
package pit12.feature.sync.api;

import com.google.gson.JsonObject;

public interface Sync {
    JsonObject state();

    JsonObject onlineState();

    void addListener(Runnable listener);

    void removeListener(Runnable listener);

    void createChannel();

    void joinChannel(String channelId, String inviteToken, String password);

    void setProvider(String provider);

    void setRegion(String region);

    void setSelfHostedUrl(String url);

    void setNickname(String nickname);

    void regenerateKey();

    void importKey(String privateKey);

    void requestInvite();

    void setJoinPolicy(String mode, String password);

    void revokeInvite(String inviteId);

    void leaveChannel();

    void dissolveChannel();

    void refreshProfiles();

    void importProfile(String ownerId, String profileId);

    void setUploads(String profiles, boolean relations);

    void setSyncSelection(boolean profiles, boolean relations);

    boolean relationsReadOnly();

    void setAccess(String memberId, String role, boolean writeProfiles, boolean writeRelations);

    void removeMember(String memberId);
}
