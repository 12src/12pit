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
package pit12.feature.webui;

import com.google.gson.JsonObject;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class WebUiRoutes {
    static final class Route {
        final String method;
        final String[] segments;
        final int bodyLimit;
        final Function<Request, Object> handler;
        final Function<Request, Object> lookup;

        Route(String method, String path, int bodyLimit, Function<Request, Object> handler,
                Function<Request, Object> lookup) {
            this.method = method;
            segments = path.split("/", -1);
            this.bodyLimit = bodyLimit;
            this.handler = handler;
            this.lookup = lookup;
        }

        Map<String, String> match(String[] path) {
            if (segments.length != path.length)
                return null;
            Map<String, String> parameters = new HashMap<>();
            for (int index = 0; index < segments.length; index++) {
                String segment = segments[index];
                if (segment.startsWith("{")) {
                    if (path[index].isEmpty())
                        return null;
                    parameters.put(segment.substring(1, segment.length() - 1), path[index]);
                } else if (!segment.equals(path[index])) {
                    return null;
                }
            }
            return parameters;
        }
    }
    static final class Request {
        final Route route;
        final Map<String, String> parameters;
        final Map<String, String> query = new LinkedHashMap<>();
        JsonObject body;

        Request(Route route, URI uri, Map<String, String> parameters)
                throws UnsupportedEncodingException {
            this.route = route;
            this.parameters = parameters;
            String rawQuery = uri.getRawQuery();
            if (rawQuery != null) {
                for (String part : rawQuery.split("&")) {
                    String[] pair = part.split("=", 2);
                    query.put(URLDecoder.decode(pair[0], "UTF-8"),
                            pair.length == 1 ? "" : URLDecoder.decode(pair[1], "UTF-8"));
                }
            }
        }
    }

    private final Map<String, Route> routes = new HashMap<>();
    private final List<Route> templates = new ArrayList<>();

    void register(String method, String path, int bodyLimit, Function<Request, Object> handler) {
        add(method, path, bodyLimit, handler, null);
    }

    /** The lookup runs on the HTTP thread; a miss runs the handler on the client thread. */
    void registerCached(String method, String path, Function<Request, Object> lookup,
            Function<Request, Object> handler) {
        add(method, path, 0, handler, lookup);
    }

    private void add(String method, String path, int bodyLimit, Function<Request, Object> handler,
            Function<Request, Object> lookup) {
        String key = method + " " + path;
        Route route = new Route(method, path, bodyLimit, handler, lookup);
        if (routes.putIfAbsent(key, route) != null)
            throw new IllegalStateException("Duplicate interface route: " + key);
        if (path.contains("{"))
            templates.add(route);
    }

    Request find(String method, URI uri) throws UnsupportedEncodingException {
        Route exact = routes.get(method + " " + uri.getPath());
        if (exact != null)
            return new Request(exact, uri, Collections.emptyMap());
        String[] path = uri.getPath().split("/", -1);
        for (Route route : templates) {
            if (!route.method.equals(method))
                continue;
            Map<String, String> parameters = route.match(path);
            if (parameters != null)
                return new Request(route, uri, parameters);
        }
        return null;
    }

    String allowedMethods(String path) {
        List<String> methods = new ArrayList<>();
        String[] segments = path.split("/", -1);
        for (Route route : routes.values()) {
            if (route.match(segments) != null && !methods.contains(route.method))
                methods.add(route.method);
        }
        return String.join(", ", methods);
    }
}
