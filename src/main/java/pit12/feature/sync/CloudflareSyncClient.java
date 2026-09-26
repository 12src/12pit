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
package pit12.feature.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

final class CloudflareSyncClient extends WebSocketClient {
    interface Listener {
        void onSnapshot(CloudflareSyncClient client, JsonObject snapshot);

        void onRelationPatch(CloudflareSyncClient client, JsonObject patch);

        void onRelationConflicts(CloudflareSyncClient client, com.google.gson.JsonArray conflicts);

        void onInvite(CloudflareSyncClient client, String id, String token, long expiresAt);

        void onReady(CloudflareSyncClient client);

        void onLeft(CloudflareSyncClient client);

        void onDissolved(CloudflareSyncClient client);

        void onProfiles(CloudflareSyncClient client, com.google.gson.JsonArray entries);

        void onProfile(CloudflareSyncClient client, String data);

        void onError(CloudflareSyncClient client, String message);

        void onClosed(CloudflareSyncClient client, String message);
    }

    private static final Logger LOGGER = Logger.getLogger(CloudflareSyncClient.class.getName());
    private final SyncIdentity identity;
    private final String channelId;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();

    CloudflareSyncClient(URI uri, SyncIdentity identity, String channelId, Listener listener) {
        super(uri);
        this.identity = identity;
        this.channelId = channelId;
        this.listener = listener;
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        closed.set(false);
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject value = new JsonParser().parse(message).getAsJsonObject();
            String type = value.get("type").getAsString();
            if ("challenge".equals(type)) {
                String nonce = value.get("nonce").getAsString();
                String signature = identity.sign("12pit-sync:" + channelId + ":" + nonce);
                JsonObject auth = new JsonObject();
                auth.addProperty("type", "auth");
                auth.addProperty("publicKey", identity.publicKey());
                auth.addProperty("signature", signature);
                send(auth.toString());
            } else if ("ready".equals(type)) {
                listener.onReady(this);
            } else if ("snapshot".equals(type)) {
                listener.onSnapshot(this, value);
            } else if ("relationPatch".equals(type)) {
                listener.onRelationPatch(this, value);
            } else if ("relationConflicts".equals(type)) {
                listener.onRelationConflicts(this, value.getAsJsonArray("conflicts"));
            } else if ("invite".equals(type)) {
                listener.onInvite(this, value.get("id").getAsString(),
                        value.get("token").getAsString(), value.get("expiresAt").getAsLong());
            } else if ("left".equals(type)) {
                listener.onLeft(this);
            } else if ("dissolved".equals(type)) {
                listener.onDissolved(this);
            } else if ("profiles".equals(type)) {
                listener.onProfiles(this, value.getAsJsonArray("entries"));
            } else if ("profile".equals(type)) {
                listener.onProfile(this, value.get("data").getAsString());
            } else if ("error".equals(type)) {
                listener.onError(this, value.get("message").getAsString());
            }
        } catch (GeneralSecurityException | RuntimeException failure) {
            listener.onError(this,
                    failure.getMessage() == null ? "Invalid sync message" : failure.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (closed.compareAndSet(false, true)) {
            listener.onClosed(this,
                    reason == null || reason.isEmpty() ? "Connection closed" : reason);
        }
    }

    @Override
    public void onError(Exception failure) {
        LOGGER.log(Level.FINE, "Cloudflare sync connection failed", failure);
        listener.onError(this,
                failure.getMessage() == null ? "Connection failed" : failure.getMessage());
    }

    void command(JsonObject command) {
        if (isOpen()) {
            send(command.toString());
        }
    }
}
