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
package pit12.platform.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pit12.feature.client.discordrpc.DiscordRpcConnection;

public final class DiscordIpcConnection implements DiscordRpcConnection {
    private static final Logger LOGGER = LogManager.getLogger(DiscordIpcConnection.class);
    private static final long HANDSHAKE_TIMEOUT = TimeUnit.SECONDS.toNanos(5);
    private final DiscordIpcTransport transport;
    private final DataInputStream input;
    private final DataOutputStream output;

    DiscordIpcConnection(DiscordIpcTransport transport) {
        this.transport = transport;
        input = new DataInputStream(new BufferedInputStream(transport.input));
        output = new DataOutputStream(new BufferedOutputStream(transport.output));
    }

    /** Returns null if there's no local Discord IPC endpoint. */
    public static DiscordRpcConnection open() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            for (int index = 0; index < 10; index++) {
                try {
                    String path = "\\\\?\\pipe\\discord-ipc-" + index;
                    return new DiscordIpcConnection(new WindowsPipeTransport(path));
                } catch (IOException unavailable) {
                    LOGGER.trace("Discord IPC endpoint {} is unavailable", index, unavailable);
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
                        return new DiscordIpcConnection(new UnixSocketTransport(endpoint));
                    } catch (IOException failure) {
                        LOGGER.trace("Discord IPC endpoint is unavailable: {}", endpoint, failure);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void publish(String applicationId, JsonObject activity) throws IOException {
        // Java 8 HotSpot gives us pid@hostname here.
        String process = ManagementFactory.getRuntimeMXBean().getName();
        int pid = Integer.parseInt(process.substring(0, process.indexOf('@')));
        JsonObject handshake = new JsonObject();
        handshake.addProperty("v", 1);
        handshake.addProperty("client_id", applicationId);
        send(0, handshake.toString().getBytes(StandardCharsets.UTF_8));
        JsonObject ready;
        transport.readDeadline = System.nanoTime() + HANDSHAKE_TIMEOUT;
        try {
            ready = receive();
        } finally {
            transport.readDeadline = 0;
        }
        if (!new JsonPrimitive("DISPATCH").equals(ready.get("cmd"))
                || !new JsonPrimitive("READY").equals(ready.get("evt"))) {
            throw new IOException("Discord RPC handshake was rejected: " + ready);
        }
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", activity);
        JsonObject request = new JsonObject();
        request.addProperty("cmd", "SET_ACTIVITY");
        request.addProperty("nonce", "12pit");
        request.add("args", args);
        send(1, request.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void listen() throws IOException {
        while (true) {
            JsonObject response = receive();
            if (new JsonPrimitive("ERROR").equals(response.get("evt"))) {
                LOGGER.warn("Discord rejected the RPC request: {}", response);
                return;
            }
        }
    }

    private JsonObject receive() throws IOException {
        while (true) {
            DiscordIpcTransport.waitMillis(transport.readDeadline, 1);
            int opcode = Integer.reverseBytes(input.readInt());
            int length = Integer.reverseBytes(input.readInt());
            // Discord's 64 KiB limit includes the 8-byte header.
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
