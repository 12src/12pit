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
package pit12.feature.profile;

import com.google.gson.JsonObject;
import java.util.UUID;
import pit12.feature.profile.api.ProfileSummary;
import pit12.feature.profile.storage.StoredProfile;
import pit12.runtime.config.ConfigSnapshot;

final class ProfileRecord {
    private final UUID id;
    private final JsonObject preservedRoot;
    private String name;
    private int order;
    private long updatedAt;
    private long revision;
    private ConfigSnapshot config;
    private boolean dirty;

    ProfileRecord(StoredProfile stored, ConfigSnapshot normalizedConfig) {
        id = stored.id();
        name = stored.name();
        order = stored.order();
        updatedAt = stored.updatedAt();
        revision = stored.revision();
        config = normalizedConfig;
        preservedRoot = stored.preservedRoot();
    }

    ProfileRecord(UUID id, String name, int order, ConfigSnapshot config) {
        this.id = id;
        this.name = name;
        this.order = order;
        updatedAt = System.currentTimeMillis();
        revision = 1L;
        this.config = config;
        preservedRoot = new JsonObject();
        dirty = true;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    void name(String name) {
        this.name = name;
    }

    int order() {
        return order;
    }

    void order(int order) {
        this.order = order;
    }

    long revision() {
        return revision;
    }

    ConfigSnapshot config() {
        return config;
    }

    void config(ConfigSnapshot config) {
        this.config = config;
    }

    boolean dirty() {
        return dirty;
    }

    void touch() {
        revision++;
        updatedAt = System.currentTimeMillis();
        dirty = true;
    }

    void persisted(long persistedRevision) {
        // A completed older write must not make a newer in-memory revision appear durable.
        if (revision == persistedRevision) {
            dirty = false;
        }
    }

    ProfileSummary summary() {
        return new ProfileSummary(id, name);
    }

    StoredProfile stored() {
        return new StoredProfile(id, name, order, updatedAt, revision, config, preservedRoot);
    }
}
