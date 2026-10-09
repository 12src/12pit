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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import pit12.Pit12;
import pit12.runtime.config.HudConfig;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentFormat;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.languages.Languages;
import pit12.shared.text.PlayerNameFormatter;

final class PlayerListHud implements HudElement {
    private static final int GAP = 10;
    private static final int EDGE_PADDING = 5;
    private static final int ARROW_SIZE = 8;
    private static final int MUTED_COLOR = 0xFFB0B0B0;
    private static final String SPAWN_TEXT = "SPAWN";
    private static final float NEAR_DISTANCE = 5.0F;
    private static final float FAR_DISTANCE = 50.0F;
    private static final ResourceLocation ARROW_TEXTURE =
            new ResourceLocation(Pit12.MOD_ID, "textures/gui/playerlist/arrow.png");
    private final PlayerListConfig config;
    private final HudConfig hudConfig;
    private final Minecraft minecraft;
    private final HudRenderer renderer;
    private final String[] groupNames = new String[PlayerListGroup.values().length];
    private PlayerListSnapshot snapshot = PlayerListSnapshot.empty();
    private PlayerListSnapshot sample;
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
    private boolean monospaceFont;
    private double renderLocalX;
    private double renderLocalZ;
    private double renderForwardX;
    private double renderForwardZ;
    private double renderRightX;
    private double renderRightZ;
    private boolean renderDirectionReady;
    private boolean sampleLayout;
    private boolean layoutDirty = true;

    PlayerListHud(PlayerListConfig config, HudRenderer renderer) {
        this.config = config;
        hudConfig = config.hud();
        minecraft = Minecraft.getMinecraft();
        this.renderer = renderer;
        sample = sampleSnapshot();
    }

    void localize(Languages language) {
        boolean translate = hudConfig.translateText().get();
        for (PlayerListGroup group : PlayerListGroup.values()) {
            groupNames[group.ordinal()] =
                    group == PlayerListGroup.FRIEND || group == PlayerListGroup.ENEMY
                            ? language.translate(group.displayName(), translate)
                            : group.displayName();
        }
        recalculateLayout(sampleLayout ? sample : snapshot);
    }

    void snapshot(PlayerListSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!sampleLayout) {
            recalculateLayout(snapshot);
        }
    }

    void configurationChanged() {
        sample = sampleSnapshot();
        layoutDirty = true;
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
        return config.displayName();
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
        boolean monospaceFont = hudConfig.useMonospaceFont().get();
        if (Float.compare(this.pixelScale, pixelScale) == 0
                && this.monospaceFont == monospaceFont) {
            return;
        }
        this.pixelScale = pixelScale;
        this.monospaceFont = monospaceFont;
        renderer.resize(pixelScale);
        recalculateLayout();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
        if (layoutDirty || editing != sampleLayout) {
            sampleLayout = editing;
            recalculateLayout(editing ? sample : snapshot);
        }
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        PlayerListSnapshot content = editing ? sample : snapshot;
        prepareRenderDirection(partialTicks);
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
                renderer.text(groupNames[group.ordinal()], EDGE_PADDING, y, 0xFFF0F0F0, hudConfig);
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
        renderer.text(entry.name(), x, y, 0xFFF0F0F0, hudConfig);
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
                renderer.text(SPAWN_TEXT, x, y, MUTED_COLOR, hudConfig);
            } else {
                String text = entry.distanceText();
                renderer.text(text, x + distanceWidth - renderer.textWidth(text, hudConfig), y,
                        entry.distanceKnown() ? distanceColor(entry.distance()) : MUTED_COLOR,
                        hudConfig);
            }
            x += distanceWidth + directionGap;
        }
        if (config.showDirection()) {
            if (entry.spawn() && !config.showDistance()) {
                renderer.text(SPAWN_TEXT, x, y, MUTED_COLOR, hudConfig);
            } else if (!entry.spawn() && entry.directionKnown()) {
                renderer.texture(ARROW_TEXTURE, x + (directionWidth - ARROW_SIZE) / 2,
                        y + (lineHeight - ARROW_SIZE) / 2, ARROW_SIZE, ARROW_SIZE,
                        entry.distanceKnown() ? distanceColor(entry.distance()) : MUTED_COLOR,
                        interpolatedDirection(entry, partialTicks));
            } else if (!entry.spawn()) {
                renderer.text("?", x + (directionWidth - renderer.textWidth("?", hudConfig)) / 2, y,
                        MUTED_COLOR, hudConfig);
            }
        }
    }

    private float interpolatedDirection(PlayerListEntry entry, float partialTicks) {
        if (entry.playerId() == null || !renderDirectionReady) {
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
        EntityPlayer localPlayer = minecraft.thePlayer;
        if (!config.showDirection() || localPlayer == null) {
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
        renderRightX = -Math.cos(yawRadians);
        renderRightZ = -Math.sin(yawRadians);
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
            renderer.text(text, x, y, 0xFFD8D8D8, hudConfig);
        }
    }

    private void recalculateLayout() {
        recalculateLayout(sampleLayout ? sample : snapshot);
    }

    private void recalculateLayout(PlayerListSnapshot content) {
        layoutDirty = false;
        nameWidth = 0;
        leggingsWidth = 0;
        heldItemWidth = 0;
        distanceWidth = 0;
        int fontHeight = renderer.fontHeight("", hudConfig);
        directionWidth = config.showDirection() ? ARROW_SIZE : 0;
        directionGap = Math.max(1, renderer.textWidth(" ", hudConfig));
        for (List<PlayerListEntry> entries : content.entries().values()) {
            for (PlayerListEntry entry : entries) {
                nameWidth = Math.max(nameWidth, renderer.textWidth(entry.name(), hudConfig));
                fontHeight = Math.max(fontHeight, renderer.fontHeight(entry.name(), hudConfig));
                if (config.showLeggings() && entry.leggingsText() != null) {
                    leggingsWidth = Math.max(leggingsWidth,
                            renderer.textWidth(entry.leggingsText(), hudConfig));
                    fontHeight = Math.max(fontHeight,
                            renderer.fontHeight(entry.leggingsText(), hudConfig));
                }
                if (config.showHeldItem() && entry.heldItemText() != null) {
                    heldItemWidth = Math.max(heldItemWidth,
                            renderer.textWidth(entry.heldItemText(), hudConfig));
                    fontHeight = Math.max(fontHeight,
                            renderer.fontHeight(entry.heldItemText(), hudConfig));
                }
                if (config.showDistance()) {
                    String text = entry.spawn() ? SPAWN_TEXT : entry.distanceText();
                    distanceWidth = Math.max(distanceWidth, renderer.textWidth(text, hudConfig));
                    fontHeight = Math.max(fontHeight, renderer.fontHeight(text, hudConfig));
                }
                if (config.showDirection() && !config.showDistance() && entry.spawn()) {
                    directionWidth =
                            Math.max(directionWidth, renderer.textWidth(SPAWN_TEXT, hudConfig));
                }
            }
        }
        if (config.showGroupName()) {
            for (PlayerListGroup group : PlayerListGroup.values()) {
                if (!content.entries(group).isEmpty()) {
                    nameWidth = Math.max(nameWidth,
                            renderer.textWidth(groupNames[group.ordinal()], hudConfig));
                    fontHeight = Math.max(fontHeight,
                            renderer.fontHeight(groupNames[group.ordinal()], hudConfig));
                }
            }
        }
        lineHeight = fontHeight + 2;
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
        height = Math.max(lineHeight, height);
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

    private PlayerListSnapshot sampleSnapshot() {
        String firstName = "§5[§f§l113§5] §6PlayerOne §e✫ §6300g";
        String secondName = "§f[§e54§f] §aPlayerTwo §c♨";
        String darkName = "§9[§320§9] §aPlayerThree §b400g";
        if (config.shortPlayerNames()) {
            firstName = PlayerNameFormatter.shorten(firstName);
            secondName = PlayerNameFormatter.shorten(secondName);
            darkName = PlayerNameFormatter.shorten(darkName);
        }
        Map<PlayerListGroup, List<PlayerListEntry>> groups =
                new EnumMap<PlayerListGroup, List<PlayerListEntry>>(PlayerListGroup.class);
        groups.put(PlayerListGroup.REGULARITY,
                Arrays.asList(
                        new PlayerListEntry(null, 0, firstName,
                                sampleEnchantments(new PitEnchantment[] {PitEnchantment.Regularity,
                                        PitEnchantment.Gotta_go_fast}, 3, 3),
                                null, 45.0F, -135.0F, true, true, false),
                        new PlayerListEntry(null, 0, secondName,
                                sampleEnchantments(new PitEnchantment[] {PitEnchantment.Regularity,
                                        PitEnchantment.Gotta_go_fast, PitEnchantment.Solitude}, 3,
                                        3, 2),
                                null, 0.0F, 0.0F, false, false, true)));
        groups.put(PlayerListGroup.DARK,
                Collections.singletonList(new PlayerListEntry(null, 0, darkName,
                        sampleEnchantments(new PitEnchantment[] {PitEnchantment.Somber}, 1), null,
                        0.0F, 0.0F, false, false, true)));
        return PlayerListSnapshot.create(groups);
    }

    private String sampleEnchantments(PitEnchantment[] enchantments, int... levels) {
        NBTTagList entries = new NBTTagList();
        for (int index = 0; index < enchantments.length; index++) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Key", enchantments[index].getKey());
            entry.setInteger("Level", levels[index]);
            entries.appendTag(entry);
        }
        NBTTagCompound attributes = new NBTTagCompound();
        attributes.setTag("CustomEnchants", entries);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("ExtraAttributes", attributes);
        ItemStack stack = new ItemStack(Items.leather_leggings);
        stack.setTagCompound(tag);
        return PitEnchantmentFormat.format(PitEnchantmentReader.read(stack),
                config.enchantmentFormat());
    }
}
