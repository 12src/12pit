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
package pit12.runtime.config.profile.storage;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pit12.runtime.config.Setting.StorageType.INTEGER;

import java.io.StringReader;
import java.util.Map;
import org.junit.Test;
import pit12.runtime.config.profile.storage.ProfileCodec.DecodeResult;

public final class ProfileCodecTest {
    private static final String OLD_SETTING = "\"old\": {\"foo\": 1}";

    @Test
    public void readsAnOldValueAfterTheSettingMovesAndChangesName() {
        ProfileCodec codec = codecWithAlias();
        DecodeResult result = decode(codec, OLD_SETTING);
        assertEquals(1, currentSettings(result).get("bar"));
    }

    @Test
    public void readsAnOldValueWhenTheSettingKeepsItsName() {
        LegacyProfileSettings legacy = new LegacyProfileSettings();
        legacy.registerSameKeys("new", "old", "bar");
        DecodeResult result = decode(codec(legacy), "\"old\": {\"bar\": 3}");
        assertEquals(3, currentSettings(result).get("bar"));
    }

    @Test
    public void prefersTheCurrentValueWhenBothKeysArePresent() {
        ProfileCodec codec = codecWithAlias();
        DecodeResult result = decode(codec, OLD_SETTING + ", \"new\": {\"bar\": 2}");
        assertEquals(2, currentSettings(result).get("bar"));
    }

    @Test
    public void leavesAnExplicitNullUnsetWithoutWarning() {
        ProfileCodec codec = codecWithAlias();
        DecodeResult result = decode(codec, OLD_SETTING + ", \"new\": {\"bar\": null}");
        assertFalse(currentSettings(result).containsKey("bar"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void warnsAboutAnInvalidCurrentValueWithoutSubstitutingTheOldValue() {
        ProfileCodec codec = codecWithAlias();
        DecodeResult result = decode(codec, OLD_SETTING + ", \"new\": {\"bar\": \"x\"}");
        assertFalse(currentSettings(result).containsKey("bar"));
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    public void ignoresTheOldValueWhenNoRuleIsConfigured() {
        ProfileCodec codec = codec(new LegacyProfileSettings());
        DecodeResult result = decode(codec, OLD_SETTING);
        assertFalse(currentSettings(result).containsKey("bar"));
    }

    @Test
    public void ignoresALegacyGroupThatIsNotAnObject() {
        ProfileCodec codec = codecWithAlias();
        DecodeResult result = decode(codec, "\"old\": []");
        assertFalse(currentSettings(result).containsKey("bar"));
    }

    @Test
    public void canReopenASavedProfileWithoutTheLegacyRule() {
        ProfileCodec legacyCodec = codecWithAlias();
        StoredProfile profile = decode(legacyCodec, OLD_SETTING).profile();
        ProfileCodec currentCodec = codec(new LegacyProfileSettings());
        DecodeResult reopened = currentCodec.decode(new StringReader(legacyCodec.encode(profile)));
        assertEquals(1, currentSettings(reopened).get("bar"));
    }

    private static DecodeResult decode(ProfileCodec codec, String features) {
        return codec.decode(new StringReader("{\"schemaVersion\": 1,"
                + "\"id\": \"00000000-0000-0000-0000-000000000001\","
                + "\"name\": \"Default\", \"order\": 0, \"features\": {" + features + "}}"));
    }

    private static Map<String, Object> currentSettings(DecodeResult result) {
        return result.profile().config().feature("new");
    }

    private static ProfileCodec codecWithAlias() {
        LegacyProfileSettings legacy = new LegacyProfileSettings();
        legacy.registerAlias("new", "bar", "old", "foo");
        return codec(legacy);
    }

    private static ProfileCodec codec(LegacyProfileSettings legacy) {
        ProfileSchema schema = new ProfileSchema(singletonMap("new", singletonMap("bar", INTEGER)));
        return new ProfileCodec(schema, legacy);
    }
}
