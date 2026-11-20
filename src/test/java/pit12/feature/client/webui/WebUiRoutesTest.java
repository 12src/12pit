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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

public final class WebUiRoutesTest {
    @Test
    public void aLiteralPathTakesPriorityOverAnEarlierTemplate() throws Exception {
        WebUiRoutes routes = new WebUiRoutes();
        routes.register("GET", "/api/items/{id}", 0, request -> "template");
        routes.register("GET", "/api/items/all", 0, request -> "literal");
        WebUiRoutes.Request request = routes.find("GET", URI.create("/api/items/all"));
        assertEquals("literal", request.route.handler.apply(request));
    }

    @Test
    public void templateRejectsEmptySegmentAndDecodesQueryParameters() throws Exception {
        WebUiRoutes routes = new WebUiRoutes();
        routes.register("GET", "/api/items/{id}", 0, request -> request.parameters.get("id"));
        assertNull(routes.find("GET", URI.create("/api/items/")));
        WebUiRoutes.Request matched =
                routes.find("GET", URI.create("/api/items/foo?name=hello%20world&flag"));
        assertEquals("foo", matched.parameters.get("id"));
        assertEquals("hello world", matched.query.get("name"));
        assertEquals("", matched.query.get("flag"));
    }

    @Test
    public void reportsAllowedMethodsForMatchingPathsAndRejectsDuplicateRoutes() {
        WebUiRoutes routes = new WebUiRoutes();
        routes.register("GET", "/api/items/{id}", 0, request -> "get");
        routes.register("DELETE", "/api/items/{id}", 0, request -> "delete");
        String allowed = routes.allowedMethods("/api/items/foo");
        assertEquals(new HashSet<>(Arrays.asList("GET", "DELETE")),
                new HashSet<>(Arrays.asList(allowed.split(", "))));
        assertTrue(routes.allowedMethods("/api/items/").isEmpty());
        assertThrows(IllegalStateException.class,
                () -> routes.register("GET", "/api/items/{id}", 0, request -> "duplicate"));
    }
}
