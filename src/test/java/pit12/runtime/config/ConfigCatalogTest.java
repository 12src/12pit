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
package pit12.runtime.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import pit12.shared.concurrent.ClientThread;

public final class ConfigCatalogTest {
    private final ConfigCatalog catalog = new ConfigCatalog(ClientThread.current());
    private final TestConfig first = new TestConfig("first");
    private final TestConfig second = new TestConfig("second");

    @Before
    public void registerConfigs() {
        catalog.register(first);
        catalog.register(second);
        catalog.freeze();
    }

    @Test
    public void batchNotificationsExposeAllUpdatedValues() {
        List<ConfigChangeSet> notifications = new ArrayList<>();
        catalog.addListener(changes -> {
            assertEquals(7, (int) first.limit.get());
            assertEquals(9, (int) second.limit.get());
            notifications.add(changes);
        });
        catalog.apply(values(7, 9));
        assertEquals(1, notifications.size());
        Set<String> changedSettings = new HashSet<>();
        notifications.get(0)
                .forEach((feature, setting) -> changedSettings.add(feature + "." + setting));
        assertEquals(new HashSet<>(Arrays.asList("first.limit", "second.limit")), changedSettings);
    }

    @Test
    public void anInvalidBatchPreservesAllValuesAndSendsNoNotification() {
        first.limit.set(4);
        second.limit.set(6);
        List<ConfigChangeSet> notifications = new ArrayList<>();
        catalog.addListener(notifications::add);
        try {
            catalog.apply(values(8, 11));
            fail("An invalid batch should fail");
        } catch (IllegalArgumentException expected) {
            assertEquals(4, (int) first.limit.get());
            assertEquals(6, (int) second.limit.get());
            assertTrue(notifications.isEmpty());
        }
    }

    @Test
    public void unchangedValuesSendNoNotification() {
        catalog.apply(values(4, 6));
        List<ConfigChangeSet> notifications = new ArrayList<>();
        catalog.addListener(notifications::add);
        catalog.apply(values(4, 6));
        first.limit.set(4);
        second.limit.set(6);
        assertTrue(notifications.isEmpty());
    }

    @Test
    public void omittedSettingsAndFeaturesReturnToDefaults() {
        catalog.apply(values(4, 6));
        first.setEnabled(false);
        second.setEnabled(false);
        catalog.apply(new ConfigSnapshot(
                Collections.singletonMap(first.id(), Collections.singletonMap("limit", 7))));
        assertEquals(7, (int) first.limit.get());
        assertEquals(second.limit.defaultValue(), second.limit.get());
        assertTrue(first.enabled());
        assertTrue(second.enabled());
    }

    @Test
    public void savedValuesRecoverEachInvalidSettingSeparately() {
        List<String> problems = new ArrayList<>();
        ConfigSnapshot recovered = catalog.recoverSavedValues(values(5, 11), problems::add);
        catalog.apply(recovered);
        assertEquals(5, (int) first.limit.get());
        assertEquals(second.limit.defaultValue(), second.limit.get());
        assertEquals(1, problems.size());
    }

    private ConfigSnapshot values(Object firstValue, Object secondValue) {
        Map<String, Map<String, Object>> features = new LinkedHashMap<>();
        features.put(first.id(), Collections.singletonMap("limit", firstValue));
        features.put(second.id(), Collections.singletonMap("limit", secondValue));
        return new ConfigSnapshot(features);
    }

    private static final class TestConfig extends FeatureConfig {
        final IntegerSetting limit = integerSliderSetting("limit", "Limit", "", 2, 0, 10, 1);

        TestConfig(String id) {
            super(id, id, null, "");
        }
    }
}
