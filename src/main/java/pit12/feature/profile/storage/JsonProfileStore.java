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
package pit12.feature.profile.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonProfileStore {
    private static final Logger LOGGER = Logger.getLogger(JsonProfileStore.class.getName());
    private static final String STATE_FILE = "profile-state.json";
    private final Path directory;
    private final ProfileCodec codec;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonProfileStore(Path directory, ProfileCodec codec) {
        this.directory = directory;
        this.codec = codec;
    }

    public LoadedProfiles load() {
        ArrayList<ProfileLoadProblem> problems = new ArrayList<ProfileLoadProblem>();
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            LOGGER.log(Level.WARNING, "Failed to create profile directory " + directory, failure);
            problems.add(new ProfileLoadProblem(directory, "Profile directory is unavailable"));
            return new LoadedProfiles(Collections.<StoredProfile>emptyList(), null, problems,
                    false);
        }
        UUID activeProfileId = readActiveProfileId(problems);
        ArrayList<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
            for (Path entry : entries) {
                if (!STATE_FILE.equals(entry.getFileName().toString())) {
                    files.add(entry);
                }
            }
        } catch (IOException failure) {
            LOGGER.log(Level.WARNING, "Failed to scan profile directory " + directory, failure);
            problems.add(new ProfileLoadProblem(directory, "Profile directory cannot be read"));
            return new LoadedProfiles(Collections.<StoredProfile>emptyList(), activeProfileId,
                    problems, false);
        }
        Collections.sort(files, Comparator.comparing(path -> path.getFileName().toString()));
        ArrayList<StoredProfile> profiles = new ArrayList<StoredProfile>();
        for (Path file : files) {
            String filename = file.getFileName().toString();
            String idText = filename.substring(0, filename.length() - ".json".length());
            UUID expectedId;
            try {
                expectedId = UUID.fromString(idText);
            } catch (IllegalArgumentException failure) {
                LOGGER.warning("Ignoring non-profile JSON file " + file);
                problems.add(new ProfileLoadProblem(file, "Filename is not a profile UUID"));
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ProfileCodec.DecodeResult result = codec.decode(expectedId, reader);
                profiles.add(result.profile());
                for (String warning : result.warnings()) {
                    LOGGER.warning("Profile load warning for " + file + ": " + warning);
                    problems.add(new ProfileLoadProblem(file, warning));
                }
            } catch (RuntimeException | IOException failure) {
                LOGGER.log(Level.WARNING, "Failed to load profile " + file, failure);
                problems.add(new ProfileLoadProblem(file,
                        failure.getMessage() == null ? "Profile could not be loaded"
                                : failure.getMessage()));
            }
        }
        Collections.sort(profiles,
                Comparator.comparingInt(StoredProfile::order).thenComparing(StoredProfile::id));
        return new LoadedProfiles(profiles, activeProfileId, problems, true);
    }

    public void writeProfile(StoredProfile profile) throws IOException {
        // UUID identity keeps renames and untrusted display names out of filesystem paths.
        writeAtomically(directory.resolve(profile.id().toString() + ".json"),
                codec.encode(profile));
    }

    public void writeState(UUID activeProfileId) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(ProfileCodec.SCHEMA_VERSION));
        root.addProperty("activeProfileId", activeProfileId.toString());
        writeAtomically(directory.resolve(STATE_FILE), gson.toJson(root));
    }

    public void moveToTrash(UUID profileId) throws IOException {
        Path source = directory.resolve(profileId.toString() + ".json");
        if (!Files.exists(source)) {
            return;
        }
        Path trash = directory.resolve("trash");
        Files.createDirectories(trash);
        String baseName = profileId.toString() + "-" + System.currentTimeMillis();
        Path target = trash.resolve(baseName + ".json");
        int suffix = 1;
        while (Files.exists(target)) {
            target = trash.resolve(baseName + "-" + suffix++ + ".json");
        }
        Files.move(source, target);
    }

    private UUID readActiveProfileId(List<ProfileLoadProblem> problems) {
        Path statePath = directory.resolve(STATE_FILE);
        if (!Files.exists(statePath)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("root must be a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement version = root.get("schemaVersion");
            if (version == null || !version.isJsonPrimitive()
                    || !version.getAsJsonPrimitive().isNumber()
                    || Integer.parseInt(version.getAsString()) != ProfileCodec.SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported schemaVersion");
            }
            JsonElement active = root.get("activeProfileId");
            if (active == null || !active.isJsonPrimitive()
                    || !active.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("$.activeProfileId must be a UUID string");
            }
            return UUID.fromString(active.getAsString());
        } catch (RuntimeException | IOException failure) {
            LOGGER.log(Level.WARNING, "Failed to load profile state " + statePath, failure);
            problems.add(new ProfileLoadProblem(statePath,
                    failure.getMessage() == null ? "Profile state could not be loaded"
                            : failure.getMessage()));
            return null;
        }
    }

    private void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(directory);
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        boolean moved = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile());
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write(content);
                writer.flush();
                output.getFD().sync();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
