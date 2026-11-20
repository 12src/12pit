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
package pit12.feature.client.webui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static pit12.feature.client.relation.api.Relation.ENEMY;
import static pit12.feature.client.relation.api.Relation.FRIEND;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Test;
import pit12.feature.client.relation.api.IdentityLookupState;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationEntry;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.api.RelationReadiness;
import pit12.feature.client.relation.api.Relations;
import pit12.feature.combat.swap.api.SwapBindings;
import pit12.runtime.config.profile.api.ProfileMutationResult;
import pit12.runtime.config.profile.api.ProfileSummary;
import pit12.runtime.config.profile.api.Profiles;
import pit12.runtime.config.profile.api.ProfilesSnapshot;
import pit12.shared.result.OperationResult;

public final class SettingsTransferTest {
    private final LocalSettings local = new LocalSettings();
    private final SettingsTransfer transfer = new SettingsTransfer(local, local, local);

    @Test
    public void anOutdatedPreviewCannotOverwriteNewLocalChanges() {
        JsonObject request = previewFriendReplacement();
        local.relations.add(new RelationEntry(null, "bar", FRIEND));
        assertThrows(IllegalArgumentException.class, () -> transfer.apply(request));
        assertEquals(Collections.singletonList("bar"), names(FRIEND));
    }

    @Test
    public void replacingOneGroupKeepsTheUnselectedGroup() {
        local.relations.add(new RelationEntry(null, "bar", FRIEND));
        local.relations.add(new RelationEntry(null, "baz", ENEMY));
        JsonObject request = previewFriendReplacement();
        transfer.apply(request);
        assertEquals(Collections.singletonList("foo"), names(FRIEND));
        assertEquals(Collections.singletonList("baz"), names(ENEMY));
    }

    @Test
    public void replacingOneGroupRejectsDuplicatePlayerInUnselectedGroup() {
        local.relations.add(new RelationEntry(null, "foo", ENEMY));
        JsonObject request = previewFriendReplacement();
        assertThrows(IllegalArgumentException.class, () -> transfer.apply(request));
    }

    @Test
    public void mergeReportsConflictKindsAndAppliesChosenResolutions() {
        UUID firstId = new UUID(0, 1);
        UUID secondId = new UUID(0, 2);
        UUID thirdId = new UUID(0, 3);
        local.relations.add(new RelationEntry(firstId, "alpha", ENEMY));
        local.relations.add(new RelationEntry(secondId, "beta", FRIEND));
        local.relations.add(new RelationEntry(thirdId, "gamma", FRIEND));
        JsonObject data = new JsonParser().parse("{\"schemaVersion\":1,\"profiles\":[],"
                + "\"relations\":{\"FRIEND\":[" + "{\"uuid\":\"" + firstId
                + "\",\"name\":\"alpha\",\"relation\":\"FRIEND\"}," + "{\"uuid\":\""
                + new UUID(0, 9) + "\",\"name\":\"beta\",\"relation\":\"FRIEND\"}," + "{\"uuid\":\""
                + thirdId + "\",\"name\":\"Gamma\",\"relation\":\"FRIEND\"},"
                + "{\"uuid\":null,\"name\":\"delta\",\"relation\":\"FRIEND\"}]}}")
                .getAsJsonObject();
        JsonObject previewRequest = new JsonObject();
        previewRequest.add("data", data);
        JsonObject preview = transfer.preview(previewRequest);
        JsonArray conflicts = preview.getAsJsonArray("conflicts");
        assertEquals(3, conflicts.size());
        assertEquals("group", conflicts.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("identity", conflicts.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals("name", conflicts.get(2).getAsJsonObject().get("kind").getAsString());
        JsonObject applyRequest =
                new JsonParser()
                        .parse("{\"profiles\":[],\"relations\":[\"FRIEND\"],\"mode\":\"merge\","
                                + "\"resolutions\":{\"FRIEND:0\":\"imported\","
                                + "\"FRIEND:1\":\"local\",\"FRIEND:2\":\"imported\"}}")
                        .getAsJsonObject();
        applyRequest.add("data", data);
        applyRequest.add("fingerprint", preview.get("fingerprint"));
        transfer.apply(applyRequest);
        assertEquals(Collections.emptyList(), names(ENEMY));
        assertEquals(Arrays.asList("beta", "alpha", "Gamma", "delta"), names(FRIEND));
        assertEquals(secondId, local.entries(FRIEND).get(0).playerId());
    }

    @Test
    public void exportsPreviewsAndAppliesProfilesRelationsAndSwapBindings() {
        UUID profileId = new UUID(0, 5);
        local.savedProfiles.put(profileId,
                "{\"id\":\"" + profileId + "\",\"name\":\"Main\",\"schemaVersion\":1}");
        local.relations.add(new RelationEntry(null, "foo", FRIEND));
        local.bindings = "{\"slots\":[1]}";
        JsonObject request = new JsonParser()
                .parse("{\"profiles\":[\"" + profileId
                        + "\"],\"relations\":[\"FRIEND\"],\"swapBindings\":true}")
                .getAsJsonObject();
        JsonObject exported = transfer.exportData(request);
        LocalSettings targetLocal = new LocalSettings();
        SettingsTransfer target = new SettingsTransfer(targetLocal, targetLocal, targetLocal);
        JsonObject previewRequest = new JsonObject();
        previewRequest.add("data", exported);
        JsonObject preview = target.preview(previewRequest);
        request.add("data", exported);
        request.addProperty("mode", "replace");
        request.add("resolutions", new JsonObject());
        request.add("fingerprint", preview.get("fingerprint"));
        target.apply(request);
        assertEquals(1, targetLocal.importedProfiles.size());
        assertEquals(Collections.singletonList("foo"), targetLocal.entries(FRIEND).stream()
                .map(RelationEntry::name).collect(Collectors.toList()));
        assertEquals("{\"slots\":[1]}", targetLocal.bindings);
    }

    private JsonObject previewFriendReplacement() {
        JsonObject request = new JsonParser()
                .parse("{\"data\":{\"schemaVersion\":1,\"profiles\":[],"
                        + "\"relations\":{\"FRIEND\":["
                        + "{\"uuid\":null,\"name\":\"foo\",\"relation\":\"FRIEND\"}]}},"
                        + "\"profiles\":[],\"relations\":[\"FRIEND\"],\"mode\":\"replace\",\"resolutions\":{}}")
                .getAsJsonObject();
        request.add("fingerprint", transfer.preview(request).get("fingerprint"));
        return request;
    }

    private List<String> names(Relation group) {
        return local.entries(group).stream().map(RelationEntry::name).collect(Collectors.toList());
    }

    private static final class LocalSettings implements Profiles, Relations, SwapBindings {
        final Map<UUID, String> savedProfiles = new LinkedHashMap<>();
        final List<String> importedProfiles = new ArrayList<>();
        final List<RelationEntry> relations = new ArrayList<>();
        String bindings = "{}";

        @Override
        public ProfilesSnapshot snapshot() {
            List<ProfileSummary> summaries = new ArrayList<>();
            for (UUID id : savedProfiles.keySet()) {
                summaries.add(new ProfileSummary(id, "Profile"));
            }
            return new ProfilesSnapshot(ProfilesSnapshot.LoadState.READY,
                    summaries.isEmpty() ? null : summaries.get(0).id(), summaries,
                    Collections.emptyList(), false);
        }

        @Override
        public OperationResult<List<String>> exportProfiles(List<UUID> ids) {
            List<String> exported = new ArrayList<>();
            for (UUID id : ids) {
                exported.add(savedProfiles.get(id));
            }
            return OperationResult.success(exported);
        }

        @Override
        public ProfileMutationResult validateImportProfiles(List<String> profiles) {
            return ProfileMutationResult.success();
        }

        @Override
        public ProfileMutationResult importProfiles(List<String> profiles) {
            importedProfiles.addAll(profiles);
            return ProfileMutationResult.success();
        }

        @Override
        public long revision() {
            return 0;
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public RelationReadiness readiness() {
            return RelationReadiness.READY;
        }

        @Override
        public String readinessProblem() {
            return "";
        }

        @Override
        public Relation relationOf(UUID playerId) {
            return relations.stream().filter(entry -> playerId.equals(entry.playerId()))
                    .map(RelationEntry::relation).findFirst().orElse(Relation.NONE);
        }

        @Override
        public List<RelationEntry> entries(Relation relation) {
            return relations.stream().filter(entry -> entry.relation() == relation)
                    .collect(Collectors.toList());
        }

        @Override
        public OperationResult<Void> replaceAll(List<RelationEntry> entries) {
            relations.clear();
            relations.addAll(entries);
            return OperationResult.success(null);
        }

        @Override
        public OperationResult<String> exportBindings() {
            return OperationResult.success(bindings);
        }

        @Override
        public OperationResult<Void> validateImport(String data) {
            return OperationResult.success(null);
        }

        @Override
        public OperationResult<Void> replaceBindings(String data) {
            bindings = data;
            return OperationResult.success(null);
        }

        @Override
        public ProfileMutationResult switchTo(UUID profileId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProfileMutationResult resetActive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProfileMutationResult beginCreate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProfileMutationResult rename(UUID profileId, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProfileMutationResult delete(UUID profileId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult<List<OperationResult<Void>>> changeMany(Relation relation,
                String action, List<RelationEntry> entries) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IdentityLookupState resolutionOf(String name) {
            return IdentityLookupState.UNKNOWN;
        }

        @Override
        public List<String> lookupProblems() {
            return Collections.emptyList();
        }

        @Override
        public void addListener(Runnable listener) {}

        @Override
        public void removeListener(Runnable listener) {}

        @Override
        public void addListener(RelationListener listener) {}

        @Override
        public void removeListener(RelationListener listener) {}

        @Override
        public void addChangeListener(Runnable listener) {}

        @Override
        public void removeChangeListener(Runnable listener) {}
    }
}
