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

import static pit12.feature.client.webui.WebUiJson.object;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class WebUiServer {
    private static final Logger LOGGER = LogManager.getLogger(WebUiServer.class);
    private static final String RESOURCES = "assets/pit12/web";
    private static final int PORT = 60916;
    private static final byte[] HEARTBEAT = ": keep-alive\n\n".getBytes(StandardCharsets.UTF_8);
    private final Minecraft minecraft;
    private final WebUiRoutes routes;
    private final WebUiEvents events;
    private final Gson gson = new Gson();
    private HttpServer server;
    private ExecutorService executor;

    WebUiServer(Minecraft minecraft, WebUiRoutes routes, WebUiEvents events) {
        this.minecraft = minecraft;
        this.routes = routes;
        this.events = events;
    }

    void start() throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpServer created;
        try {
            created = HttpServer.create(new InetSocketAddress(loopback, PORT), 0);
        } catch (BindException occupied) {
            created = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        }
        executor = Executors.newFixedThreadPool(WebUiEvents.MAX_STREAMS + 2, runnable -> {
            Thread thread = new Thread(runnable, "12pit-web-server");
            thread.setDaemon(true);
            return thread;
        });
        created.setExecutor(executor);
        created.createContext("/", this::handle);
        created.start();
        server = created;
    }

    void stop() {
        events.clear();
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Content-Security-Policy", "frame-ancestors 'none'");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            String authority = "127.0.0.1:" + exchange.getLocalAddress().getPort();
            List<String> hosts = exchange.getRequestHeaders().get("Host");
            if (hosts == null || hosts.size() != 1 || !authority.equals(hosts.get(0))) {
                sendJson(exchange, 403, object("error", "Unexpected host"));
                return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            // noinspection HttpClientInappropriateProtocolUsageInspection
            if (origin != null && !("http://" + authority).equals(origin)) {
                sendJson(exchange, 403, object("error", "Unexpected origin"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else if ("GET".equals(exchange.getRequestMethod())) {
                serveResource(exchange, path);
            } else {
                sendJson(exchange, 405, object("error", "Method not allowed"));
            }
        } catch (JsonParseException failure) {
            sendJson(exchange, 400, object("error", "Invalid JSON"));
        } catch (IllegalArgumentException failure) {
            sendJson(exchange, 400, object("error", failure.getMessage()));
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IllegalArgumentException) {
                sendJson(exchange, 400, object("error", cause.getMessage()));
            } else {
                LOGGER.error("Configuration request failed: {} {}", exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(), cause);
                sendJson(exchange, 500, object("error", "Settings operation failed"));
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            sendJson(exchange, 503, object("error", "Settings server is stopping"));
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path)
            throws IOException, ExecutionException, InterruptedException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        // Browsers can omit Origin on cross-origin GETs.
        String site = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
        if ("cross-site".equals(site) || "same-site".equals(site)) {
            sendJson(exchange, 403, object("error", "Unexpected request site"));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/events".equals(path)) {
            serveEvents(exchange);
            return;
        }
        WebUiRoutes.Request request =
                routes.find(exchange.getRequestMethod(), exchange.getRequestURI());
        if (request == null) {
            String allowed = routes.allowedMethods(path);
            if (allowed.isEmpty()) {
                sendJson(exchange, 404, object("error", "Unknown endpoint"));
            } else {
                exchange.getResponseHeaders().set("Allow", allowed);
                sendJson(exchange, 405, object("error", "Method not allowed"));
            }
            return;
        }
        if (request.route.lookup != null) {
            Object cached = request.route.lookup.apply(request);
            if (cached != null) {
                sendJson(exchange, 200, cached);
                return;
            }
        }
        if (request.route.bodyLimit == 0) {
            sendJson(exchange, 200, onClient(() -> request.route.handler.apply(request)));
            return;
        }
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) {
            sendJson(exchange, 415, object("error", "Expected JSON"));
            return;
        }
        JsonElement parsed = new JsonParser()
                .parse(new String(readLimited(exchange.getRequestBody(), request.route.bodyLimit),
                        StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        request.body = parsed.getAsJsonObject();
        sendJson(exchange, 200, onClient(() -> request.route.handler.apply(request)));
    }

    private void serveEvents(HttpExchange exchange)
            throws IOException, ExecutionException, InterruptedException {
        WebUiEvents.Stream stream = onClient(events::subscribe);
        if (stream == null) {
            sendJson(exchange, 503, object("error", "Too many open pages"));
            return;
        }
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                while (!Thread.currentThread().isInterrupted()) {
                    if (stream.wakeup.poll(5, TimeUnit.SECONDS) == null) {
                        output.write(HEARTBEAT);
                    } else {
                        List<String> resources = onClient(() -> events.read(stream));
                        output.write(("event: change\ndata: " + gson.toJson(resources) + "\n\n")
                                .getBytes(StandardCharsets.UTF_8));
                    }
                    output.flush();
                }
            }
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException failure) {
            LOGGER.error("Could not read configuration interface state", failure.getCause());
            throw new IOException("Could not read configuration interface state",
                    failure.getCause());
        } finally {
            // The client thread also stops these workers.
            minecraft.addScheduledTask(() -> events.unsubscribe(stream));
        }
    }

    private <T> T onClient(Callable<T> action) throws ExecutionException, InterruptedException {
        return minecraft.addScheduledTask(action).get();
    }

    private void serveResource(HttpExchange exchange, String path) throws IOException {
        if ("/".equals(path)) {
            path = "/index.html";
        }
        if (!"/index.html".equals(path) && !path.startsWith("/assets/") || path.contains("..")
                || path.contains("\\")) {
            sendJson(exchange, 404, object("error", "Not found"));
            return;
        }
        try (InputStream resource =
                WebUiServer.class.getClassLoader().getResourceAsStream(RESOURCES + path)) {
            if (resource == null) {
                sendJson(exchange, 404, object("error", "Not found"));
                return;
            }
            String type = path.endsWith(".html") ? "text/html; charset=utf-8"
                    : path.endsWith(".js") ? "text/javascript; charset=utf-8"
                            : path.endsWith(".css") ? "text/css; charset=utf-8"
                                    : path.endsWith(".png") ? "image/png"
                                            : "application/octet-stream";
            byte[] bytes = readLimited(resource, 4 * 1024 * 1024);
            // Vite includes a content hash in asset filenames.
            exchange.getResponseHeaders().set("Cache-Control",
                    path.startsWith("/assets/") ? "public, max-age=31536000, immutable"
                            : "no-cache");
            sendBytes(exchange, 200, type, bytes);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8",
                gson.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int status, String type, byte[] bytes)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = input.read(chunk)) != -1) {
            if (bytes.size() + count > limit) {
                throw new IllegalArgumentException("Request or resource is too large");
            }
            bytes.write(chunk, 0, count);
        }
        return bytes.toByteArray();
    }
}
