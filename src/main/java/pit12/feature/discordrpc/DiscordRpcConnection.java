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
package pit12.feature.discordrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DiscordRpcConnection implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(DiscordRpcConnection.class.getName());
    private final Closeable transport;
    private final DataInputStream input;
    private final DataOutputStream output;

    private DiscordRpcConnection(Closeable transport, InputStream input, OutputStream output) {
        this.transport = transport;
        this.input = new DataInputStream(input);
        this.output = new DataOutputStream(output);
    }

    /** Returns null when no local Discord IPC endpoint is available. */
    static DiscordRpcConnection open() throws IOException {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            for (int index = 0; index < 10; index++) {
                try {
                    WindowsPipeTransport pipe =
                            new WindowsPipeTransport("\\\\?\\pipe\\discord-ipc-" + index);
                    return new DiscordRpcConnection(pipe, new BufferedInputStream(pipe.input),
                            new BufferedOutputStream(pipe.output));
                } catch (IOException unavailable) {
                    LOGGER.log(Level.FINE, "Discord IPC endpoint " + index + " is unavailable",
                            unavailable);
                }
            }
            return null;
        }
        Set<String> directories = new LinkedHashSet<>();
        for (String name : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String directory = System.getenv(name);
            if (directory != null && !directory.isEmpty()) {
                directories.add(directory);
            }
        }
        directories.add("/tmp");
        for (String directory : directories) {
            for (String subdirectory : new String[] {"", "app/com.discordapp.Discord",
                    "snap.discord"}) {
                for (int index = 0; index < 10; index++) {
                    File endpoint =
                            new File(new File(directory, subdirectory), "discord-ipc-" + index);
                    if (!endpoint.exists()) {
                        continue;
                    }
                    try {
                        UnixSocketTransport socket = new UnixSocketTransport(endpoint);
                        return new DiscordRpcConnection(socket,
                                new BufferedInputStream(socket.input),
                                new BufferedOutputStream(socket.output));
                    } catch (IOException failure) {
                        LOGGER.log(Level.FINE, "Discord IPC endpoint is unavailable: " + endpoint,
                                failure);
                    }
                }
            }
        }
        return null;
    }

    void publish(int pid, long startedAt) throws IOException {
        JsonObject handshake = new JsonObject();
        handshake.addProperty("v", 1);
        handshake.addProperty("client_id", "1553780738573213716");
        send(0, handshake.toString().getBytes(StandardCharsets.UTF_8));
        JsonObject ready = receive();
        if (!new JsonPrimitive("DISPATCH").equals(ready.get("cmd"))
                || !new JsonPrimitive("READY").equals(ready.get("evt"))) {
            throw new IOException("Discord RPC handshake was rejected: " + ready);
        }
        JsonObject activity = new JsonObject();
        activity.addProperty("type", 0);
        activity.addProperty("details", "A free and open-source pit mod");
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", startedAt);
        activity.add("timestamps", timestamps);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "12");
        assets.addProperty("large_text", "12pit");
        activity.add("assets", assets);
        JsonArray buttons = new JsonArray();
        buttons.add(button("GitHub", "https://github.com/12src/12pit"));
        buttons.add(button("Discord", "https://discord.gg/e9PRKMUenc"));
        activity.add("buttons", buttons);
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", activity);
        JsonObject request = new JsonObject();
        request.addProperty("cmd", "SET_ACTIVITY");
        request.addProperty("nonce", "12pit");
        request.add("args", args);
        send(1, request.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject button(String label, String url) {
        JsonObject button = new JsonObject();
        button.addProperty("label", label);
        button.addProperty("url", url);
        return button;
    }

    void listen() throws IOException {
        while (true) {
            JsonObject response = receive();
            if (new JsonPrimitive("ERROR").equals(response.get("evt"))) {
                LOGGER.warning("Discord rejected the RPC request: " + response);
                return;
            }
        }
    }

    private JsonObject receive() throws IOException {
        while (true) {
            int opcode = Integer.reverseBytes(input.readInt());
            int length = Integer.reverseBytes(input.readInt());
            // Discord limits IPC frames to 64 KiB, including the eight-byte header.
            if (length < 0 || length > 65528) {
                throw new IOException("Invalid Discord RPC frame length: " + length);
            }
            byte[] payload = new byte[length];
            input.readFully(payload);
            switch (opcode) {
                case 1:
                    try {
                        return new JsonParser().parse(new String(payload, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                    } catch (JsonParseException | IllegalStateException failure) {
                        throw new IOException("Invalid Discord RPC response", failure);
                    }
                case 2:
                    throw new EOFException("Discord closed the RPC connection");
                case 3:
                    send(4, payload);
                    break;
                case 4:
                    break;
                default:
                    throw new IOException("Invalid Discord RPC opcode: " + opcode);
            }
        }
    }

    private void send(int opcode, byte[] payload) throws IOException {
        output.writeInt(Integer.reverseBytes(opcode));
        output.writeInt(Integer.reverseBytes(payload.length));
        output.write(payload);
        output.flush();
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }
}
