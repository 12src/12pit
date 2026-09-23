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
package pit12.feature.playerlist;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import pit12.Pit12;
import pit12.runtime.config.HudConfig;
import pit12.runtime.hud.HudElement;
import pit12.shared.rendering.UiRenderer;

final class PlayerListHud implements HudElement {
    private static final float FONT_SIZE = 8.0F;
    private static final int GAP = 10;
    private static final int EDGE_PADDING = 5;
    private static final int ARROW_SIZE = 8;
    private static final int MUTED_COLOR = 0xFFB0B0B0;
    private static final String SPAWN_TEXT = "SPAWN";
    private static final float NEAR_DISTANCE = 5.0F;
    private static final float FAR_DISTANCE = 50.0F;
    private static final ResourceLocation ARROW_TEXTURE =
            new ResourceLocation(Pit12.MOD_ID, "textures/gui/playerlist/arrow.png");
    private static final PlayerListSnapshot SAMPLE = sampleSnapshot();
    private final PlayerListConfig config;
    private final HudConfig hudConfig;
    private final Minecraft minecraft;
    private final FontRenderer vanillaFont;
    private final UiRenderer renderer;
    private PlayerListSnapshot snapshot = PlayerListSnapshot.empty();
    private int nameWidth;
    private int leggingsWidth;
    private int heldItemWidth;
    private int distanceWidth;
    private int directionWidth;
    private int directionGap;
    private int lineHeight;
    private int width;
    private int height;
    private float pixelScale = Float.NaN;
    private double renderLocalX;
    private double renderLocalZ;
    private double renderForwardX;
    private double renderForwardZ;
    private double renderRightX;
    private double renderRightZ;
    private boolean renderDirectionReady;

    PlayerListHud(PlayerListConfig config) {
        this.config = config;
        hudConfig = config.hud();
        minecraft = Minecraft.getMinecraft();
        vanillaFont = minecraft.fontRendererObj;
        renderer = new UiRenderer(minecraft,
                new ResourceLocation(Pit12.MOD_ID, "fonts/montserrat-regular.ttf"),
                new ResourceLocation(Pit12.MOD_ID, "fonts/noto-sans-sc.otf"));
    }

    void snapshot(PlayerListSnapshot snapshot) {
        this.snapshot = snapshot;
        lineHeight = fontHeight() + 2;
        recalculateLayout();
    }

    void close() {
        renderer.close();
        pixelScale = Float.NaN;
    }

    @Override
    public String id() {
        return "player-list";
    }

    @Override
    public String displayName() {
        return "Player List";
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public HudConfig config() {
        return hudConfig;
    }

    @Override
    public void resize(float pixelScale) {
        float normalizedScale = Math.max(0.01F, pixelScale);
        if (Float.compare(this.pixelScale, normalizedScale) == 0) {
            return;
        }
        this.pixelScale = normalizedScale;
        renderer.resize(normalizedScale);
        lineHeight = fontHeight() + 2;
        recalculateLayout();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        PlayerListSnapshot content = snapshot;
        prepareRenderDirection(partialTicks);
        if (editing && content.isEmpty()) {
            content = SAMPLE;
            recalculateLayout(content);
        }
        int y = EDGE_PADDING;
        boolean hasGroup = false;
        for (PlayerListGroup group : PlayerListGroup.values()) {
            List<PlayerListEntry> entries = content.entries(group);
            if (entries.isEmpty()) {
                continue;
            }
            if (hasGroup) {
                y += lineHeight;
            }
            if (config.showGroupName()) {
                text(group.displayName(), EDGE_PADDING, y, 0xFFF0F0F0);
                y += lineHeight;
            }
            for (PlayerListEntry entry : entries) {
                renderEntry(entry, y, partialTicks);
                y += lineHeight;
            }
            hasGroup = true;
        }
    }

    private void renderEntry(PlayerListEntry entry, int y, float partialTicks) {
        int x = EDGE_PADDING;
        text(entry.name(), x, y, 0xFFF0F0F0);
        x += nameWidth + GAP;
        if (config.showLeggings()) {
            renderOptional(entry.leggingsText(), x, y);
            x += leggingsWidth + GAP;
        }
        if (config.showHeldItem()) {
            renderOptional(entry.heldItemText(), x, y);
            x += heldItemWidth + GAP;
        }
        if (config.showDistance()) {
            if (entry.spawn()) {
                text(SPAWN_TEXT, x, y, MUTED_COLOR);
            } else {
                String text = entry.distanceText();
                if (!text.isEmpty()) {
                    text(text, x + distanceWidth - textWidth(text), y,
                            entry.distanceKnown() ? distanceColor(entry.distance()) : MUTED_COLOR);
                }
            }
            x += distanceWidth + directionGap;
        }
        if (config.showDirection()) {
            if (entry.spawn() && !config.showDistance()) {
                text(SPAWN_TEXT, x, y, MUTED_COLOR);
            } else if (!entry.spawn() && entry.directionKnown()) {
                renderer.texture(ARROW_TEXTURE, x + (directionWidth - ARROW_SIZE) / 2,
                        y + (lineHeight - ARROW_SIZE) / 2, ARROW_SIZE, ARROW_SIZE,
                        entry.distanceKnown() ? distanceColor(entry.distance()) : MUTED_COLOR,
                        interpolatedDirection(entry, partialTicks));
            } else if (!entry.spawn()) {
                text("?", x + (directionWidth - textWidth("?")) / 2, y, MUTED_COLOR);
            }
        }
    }

    private float interpolatedDirection(PlayerListEntry entry, float partialTicks) {
        if (entry.playerId() == null || !entry.directionKnown() || !renderDirectionReady) {
            return entry.direction();
        }
        WorldClient world = minecraft.theWorld;
        if (world == null) {
            return entry.direction();
        }
        Entity entity = world.getEntityByID(entry.entityId());
        // A respawn can replace the entity ID before the next snapshot update.
        EntityPlayer player =
                entity instanceof EntityPlayer && entry.playerId().equals(entity.getUniqueID())
                        ? (EntityPlayer) entity
                        : world.getPlayerEntityByUUID(entry.playerId());
        if (player == null) {
            return entry.direction();
        }
        double playerX = interpolate(player.prevPosX, player.posX, partialTicks);
        double playerZ = interpolate(player.prevPosZ, player.posZ, partialTicks);
        double dx = playerX - renderLocalX;
        double dz = playerZ - renderLocalZ;
        double forward = dx * renderForwardX + dz * renderForwardZ;
        double right = dx * renderRightX + dz * renderRightZ;
        return (float) Math.toDegrees(Math.atan2(right, forward));
    }

    private void prepareRenderDirection(float partialTicks) {
        if (!config.showDirection()) {
            renderDirectionReady = false;
            return;
        }
        EntityPlayer localPlayer = minecraft.thePlayer;
        if (localPlayer == null) {
            renderDirectionReady = false;
            return;
        }
        renderLocalX = interpolate(localPlayer.prevPosX, localPlayer.posX, partialTicks);
        renderLocalZ = interpolate(localPlayer.prevPosZ, localPlayer.posZ, partialTicks);
        float yaw = localPlayer.prevRotationYaw
                + wrapDegrees(localPlayer.rotationYaw - localPlayer.prevRotationYaw) * partialTicks;
        double yawRadians = Math.toRadians(yaw);
        renderForwardX = -Math.sin(yawRadians);
        renderForwardZ = Math.cos(yawRadians);
        renderRightX = Math.cos(yawRadians);
        renderRightZ = Math.sin(yawRadians);
        renderDirectionReady = true;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private void renderOptional(String text, int x, int y) {
        if (text != null && !text.isEmpty()) {
            text(text, x, y, 0xFFD8D8D8);
        }
    }

    private void recalculateLayout() {
        recalculateLayout(snapshot);
    }

    private void recalculateLayout(PlayerListSnapshot content) {
        nameWidth = 0;
        leggingsWidth = 0;
        heldItemWidth = 0;
        distanceWidth = 0;
        directionWidth = config.showDirection() ? ARROW_SIZE : 0;
        directionGap = Math.max(1, textWidth(" "));
        for (List<PlayerListEntry> entries : content.entries().values()) {
            for (PlayerListEntry entry : entries) {
                nameWidth = Math.max(nameWidth, textWidth(entry.name()));
                if (config.showLeggings() && entry.leggingsText() != null) {
                    leggingsWidth = Math.max(leggingsWidth, textWidth(entry.leggingsText()));
                }
                if (config.showHeldItem() && entry.heldItemText() != null) {
                    heldItemWidth = Math.max(heldItemWidth, textWidth(entry.heldItemText()));
                }
                if (config.showDistance()) {
                    distanceWidth = Math.max(distanceWidth, textWidth(entry.distanceText()));
                }
                if (config.showDirection() && !config.showDistance() && entry.spawn()) {
                    directionWidth = Math.max(directionWidth, textWidth(SPAWN_TEXT));
                }
            }
        }
        if (config.showGroupName()) {
            for (PlayerListGroup group : PlayerListGroup.values()) {
                if (!content.entries(group).isEmpty()) {
                    nameWidth = Math.max(nameWidth, textWidth(group.displayName()));
                }
            }
        }
        width = EDGE_PADDING + nameWidth;
        if (config.showLeggings()) {
            width += GAP + leggingsWidth;
        }
        if (config.showHeldItem()) {
            width += GAP + heldItemWidth;
        }
        if (config.showDistance()) {
            width += GAP + distanceWidth;
        }
        if (config.showDirection()) {
            width += (config.showDistance() ? directionGap : GAP) + directionWidth;
        }
        width += EDGE_PADDING;
        height = EDGE_PADDING;
        boolean hasGroup = false;
        for (PlayerListGroup group : PlayerListGroup.values()) {
            List<PlayerListEntry> entries = content.entries(group);
            if (!entries.isEmpty()) {
                if (hasGroup) {
                    height += lineHeight;
                }
                height += entries.size() * lineHeight;
                if (config.showGroupName()) {
                    height += lineHeight;
                }
                hasGroup = true;
            }
        }
        width = Math.max(1, width);
        height = Math.max(lineHeight, height);
    }

    private void text(String text, int x, int y, int color) {
        boolean shadow = hudConfig.textShadow().get().booleanValue();
        if (!config.useVanillaFont()) {
            renderer.text(text, x, y, FONT_SIZE, color, shadow);
            return;
        }
        if (shadow) {
            vanillaFont.drawStringWithShadow(text, x, y, color);
        } else {
            vanillaFont.drawString(text, x, y, color, false);
        }
    }

    private int textWidth(String text) {
        return config.useVanillaFont() ? vanillaFont.getStringWidth(text)
                : renderer.textWidth(text, FONT_SIZE);
    }

    private int fontHeight() {
        return config.useVanillaFont() ? vanillaFont.FONT_HEIGHT : renderer.fontHeight(FONT_SIZE);
    }

    private static int distanceColor(float distance) {
        float progress = Math.max(0.0F,
                Math.min(1.0F, (distance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE)));
        int red;
        int green;
        if (progress < 0.5F) {
            float local = progress * 2.0F;
            red = 255;
            green = Math.round(255.0F * local);
        } else {
            float local = (progress - 0.5F) * 2.0F;
            red = Math.round(255.0F * (1.0F - local));
            green = 255;
        }
        return 0xFF000000 | red << 16 | green << 8;
    }

    private static PlayerListSnapshot sampleSnapshot() {
        Map<PlayerListGroup, List<PlayerListEntry>> groups =
                new EnumMap<PlayerListGroup, List<PlayerListEntry>>(PlayerListGroup.class);
        groups.put(PlayerListGroup.REGULARITY,
                Collections.singletonList(
                        new PlayerListEntry(null, 0, "ExamplePlayer", PlayerListGroup.REGULARITY,
                                "§4REG 3", "§bSW 3", 12.0F, 18.0F, true, true, false)));
        groups.put(PlayerListGroup.DARK,
                Collections.singletonList(new PlayerListEntry(null, 0, "DarkPlayer",
                        PlayerListGroup.DARK, "§dDark 2", "", 28.0F, -42.0F, true, true, false)));
        return PlayerListSnapshot.create(groups, 2);
    }
}
