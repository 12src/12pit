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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;

public final class WebUiPreferencesTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();
    private final Thread mainThread = Thread.currentThread();
    private final BlockingQueue<Runnable> clientTasks = new LinkedBlockingQueue<>();
    private final ClientThread client =
            new ClientThread(() -> Thread.currentThread() == mainThread, clientTasks::add);
    private final ConfigCatalog catalog = new ConfigCatalog(client);
    private final WebUiConfig config = new WebUiConfig();
    private final Languages language = new Languages(client);
    private WebUiPreferences preferences;

    @Before
    public void registerConfig() {
        catalog.register(config);
        catalog.freeze();
    }

    @After
    public void stopPreferences() {
        if (preferences != null) {
            preferences.stop();
        }
    }

    @Test
    public void preservesEditsMadeWhileLoadingFallsBackInvalidLocaleAndFlushesOnStop()
            throws Exception {
        Path path = folder.getRoot().toPath().resolve("webui.json");
        Files.write(path,
                ("{\"schemaVersion\":1,\"favorites\":[\"foo\"],"
                        + "\"settings\":{\"language\":\"unknown-locale\",\"discord_rpc\":true}}")
                        .getBytes(StandardCharsets.UTF_8));
        preferences = new WebUiPreferences(catalog, config, language, path);
        preferences.start(() -> {
        });
        config.discordRpc().set(false);
        awaitClient(preferences::ready);
        assertFalse(config.discordRpc().get());
        assertEquals("en-us", config.language().get());
        assertEquals(Collections.singletonList("foo"), preferences.favorites());
        preferences.setFavorite("bar", true);
        preferences.stop();
        preferences = null;
        JsonObject saved =
                new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertFalse(saved.getAsJsonObject("settings").get("discord_rpc").getAsBoolean());
        assertEquals(2, saved.getAsJsonArray("favorites").size());
    }

    private void awaitClient(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            long remaining = deadline - System.nanoTime();
            assertTrue("Timed out waiting for preferences", remaining > 0L);
            Runnable task = clientTasks.poll(remaining, NANOSECONDS);
            assertNotNull("Expected a client task", task);
            task.run();
        }
    }
}
