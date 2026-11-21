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
package pit12.feature.render.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CosmeticModelLoader {
    private static final Logger LOGGER = LogManager.getLogger(CosmeticModelLoader.class);
    private static final String DIRECTORY = "/assets/pit12/cosmetic/";

    public CosmeticModel load(String id) {
        String path = DIRECTORY + id + ".json";
        try (InputStream stream = CosmeticModelLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                LOGGER.warn("Missing cosmetic model {}", path);
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                ResourceLocation texture =
                        new ResourceLocation("pit12", "textures/cosmetic/" + id + ".png");
                return parse(root, texture);
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Failed to load cosmetic model {}", path, failure);
            return null;
        }
    }

    private static CosmeticModel parse(JsonObject root, ResourceLocation texture) {
        CosmeticModel.Builder builder = new CosmeticModel.Builder();
        JsonArray textureSize = root.getAsJsonArray("textureSize");
        builder.textureSize(textureSize.get(0).getAsInt(), textureSize.get(1).getAsInt());
        for (JsonElement element : root.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            JsonArray uv = part.getAsJsonArray("uv");
            builder.add(part.get("inner").getAsBoolean(), uv.get(0).getAsInt(),
                    uv.get(1).getAsInt(), floats(part.getAsJsonArray("origin")),
                    integers(part.getAsJsonArray("size")),
                    floats(part.getAsJsonArray("rotationPoint")),
                    floats(part.getAsJsonArray("rotation")));
        }
        return builder.build(texture);
    }

    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).getAsFloat();
        }
        return values;
    }

    private static int[] integers(JsonArray array) {
        int[] values = new int[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).getAsInt();
        }
        return values;
    }
}
