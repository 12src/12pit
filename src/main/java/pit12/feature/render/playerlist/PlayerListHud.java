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
package pit12.feature.render.playerlist;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import pit12.runtime.config.HudConfig;
import pit12.runtime.config.HudGroupConfig;
import pit12.runtime.hud.HudElement;
import pit12.runtime.hud.HudRenderer;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentFormat;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.languages.Languages;
import pit12.shared.build.BuildConfig;
import pit12.shared.rendering.FontWidths;
import pit12.shared.text.TextCodes;

final class PlayerListHud implements HudElement {
    private static final int GAP = 15;
    private static final int EDGE_PADDING = 5;
    private static final int ARROW_SIZE = 8;
    private static final int MUTED_COLOR = 0xFFB0B0B0;
    private static final String SPAWN_TEXT = "SPAWN";
    private static final float NEAR_DISTANCE = 5.0F;
    private static final float FAR_DISTANCE = 50.0F;
    private static final ResourceLocation ARROW_TEXTURE =
            new ResourceLocation(BuildConfig.MOD_ID, "textures/gui/playerlist/arrow.png");
    private final PlayerListConfig config;
    private final HudConfig hudConfig;
    // A null group uses the combined layout.
    private final PlayerListGroup group;
    private final PlayerListGroup[] groups;
    private final String id;
    private final Minecraft minecraft;
    private final HudRenderer renderer;
    private final Languages language;
    private final String[] groupNames = new String[PlayerListGroup.values().length];
    private final Map<PlayerListGroup, int[]> distanceTextWidths =
            new EnumMap<>(PlayerListGroup.class);
    private PlayerListSnapshot snapshot = PlayerListSnapshot.empty();
    private PlayerListSnapshot sample;
    private int nameWidth;
    private int leggingsWidth;
    private int heldItemWidth;
    private int distanceWidth;
    private int directionWidth;
    private int directionGap;
    private int unknownDirectionWidth;
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
    private boolean sampleLayout;
    private boolean layoutDirty = true;
    private boolean languageDirty = true;
    private boolean unicodeFont;

    PlayerListHud(PlayerListConfig config, HudRenderer renderer, Languages language,
            PlayerListGroup group) {
        this.config = config;
        this.group = group;
        groups = group == null ? PlayerListGroup.values() : new PlayerListGroup[] {group};
        id = group == null ? "player-list" : "player-list." + group.id();
        hudConfig = group == null ? config.hud() : config.hudGroups.get(group).hud();
        minecraft = Minecraft.getMinecraft();
        this.renderer = renderer;
        this.language = language;
    }

    void localize() {
        languageDirty = true;
        layoutDirty = true;
    }

    void snapshot(PlayerListSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!sampleLayout) {
            layoutDirty = true;
        }
    }

    void configurationChanged() {
        sample = null;
        layoutDirty = true;
    }

    void close() {
        pixelScale = Float.NaN;
    }

    boolean active() {
        if (group != null) {
            return config.hudGroups.get(group).independent();
        }
        for (HudGroupConfig layout : config.hudGroups.values()) {
            if (!layout.independent()) {
                return true;
            }
        }
        return false;
    }

    private boolean includes(PlayerListGroup group) {
        return this.group == null ? !config.hudGroups.get(group).independent()
                : this.group == group;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return group == null ? config.displayName()
                : config.displayName() + ": " + TextCodes.strip(
                        language.translate(group.displayName(), hudConfig.translateText().get()));
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
        if (Float.compare(this.pixelScale, pixelScale) == 0) {
            return;
        }
        this.pixelScale = pixelScale;
        renderer.resize(pixelScale);
        layoutDirty = true;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public void prepare(float pixelScale, boolean editing) {
        resize(pixelScale);
        if (languageDirty) {
            boolean translate = hudConfig.translateText().get();
            for (PlayerListGroup group : groups) {
                groupNames[group.ordinal()] = group == PlayerListGroup.FRIEND
                        || group == PlayerListGroup.ENEMY || group == PlayerListGroup.NICK
                                ? language.translate(group.displayName(), translate)
                                : group.displayName();
            }
            languageDirty = false;
        }
        boolean unicode = minecraft.fontRendererObj.getUnicodeFlag();
        if (unicode != unicodeFont) {
            unicodeFont = unicode;
            layoutDirty = true;
        }
        if (layoutDirty || editing != sampleLayout) {
            sampleLayout = editing;
            if (editing && sample == null) {
                sample = sampleSnapshot();
            }
            recalculateLayout(editing ? sample : snapshot);
        }
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        renderer.resize(pixelScale);
        PlayerListSnapshot content = editing ? sample : snapshot;
        prepareRenderDirection(partialTicks);
        int y = EDGE_PADDING;
        boolean hasGroup = false;
        for (PlayerListGroup group : groups) {
            if (!includes(group)) {
                continue;
            }
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
            int[] textWidths = distanceTextWidths.get(group);
            for (int index = 0; index < entries.size(); index++) {
                renderEntry(entries.get(index), y, partialTicks,
                        textWidths == null ? 0 : textWidths[index]);
                y += lineHeight;
            }
            hasGroup = true;
        }
    }

    private void renderEntry(PlayerListEntry entry, int y, float partialTicks, int textWidth) {
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
                renderer.text(text, x + distanceWidth - textWidth, y,
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
                renderer.text("?", x + (directionWidth - unknownDirectionWidth) / 2, y, MUTED_COLOR,
                        hudConfig);
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
        // The entity must still match the one used for distance and spawn state.
        if (!(entity instanceof EntityPlayer) || entity.isDead
                || !entry.playerId().equals(entity.getUniqueID())) {
            return entry.direction();
        }
        EntityPlayer player = (EntityPlayer) entity;
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
        renderRightX = -renderForwardZ;
        renderRightZ = renderForwardX;
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

    private void recalculateLayout(PlayerListSnapshot content) {
        FontRenderer font = minecraft.fontRendererObj;
        layoutDirty = false;
        nameWidth = 0;
        leggingsWidth = 0;
        heldItemWidth = 0;
        distanceWidth = 0;
        directionWidth = config.showDirection() ? ARROW_SIZE : 0;
        directionGap = Math.max(1, FontWidths.measure(font, " "));
        unknownDirectionWidth = FontWidths.measure(font, "?");
        boolean showDistance = config.showDistance();
        if (!showDistance) {
            distanceTextWidths.clear();
        } else {
            distanceTextWidths.keySet().retainAll(content.entries().keySet());
        }
        for (Map.Entry<PlayerListGroup, List<PlayerListEntry>> group : content.entries()
                .entrySet()) {
            if (!includes(group.getKey())) {
                continue;
            }
            List<PlayerListEntry> entries = group.getValue();
            int[] textWidths = showDistance ? distanceTextWidths.get(group.getKey()) : null;
            if (showDistance && (textWidths == null || textWidths.length != entries.size())) {
                textWidths = new int[entries.size()];
                distanceTextWidths.put(group.getKey(), textWidths);
            }
            for (int index = 0; index < entries.size(); index++) {
                PlayerListEntry entry = entries.get(index);
                nameWidth = Math.max(nameWidth, FontWidths.measure(font, entry.name()));
                if (config.showLeggings() && entry.leggingsText() != null) {
                    leggingsWidth =
                            Math.max(leggingsWidth, FontWidths.measure(font, entry.leggingsText()));
                }
                if (config.showHeldItem() && entry.heldItemText() != null) {
                    heldItemWidth =
                            Math.max(heldItemWidth, FontWidths.measure(font, entry.heldItemText()));
                }
                if (showDistance) {
                    String text = entry.spawn() ? SPAWN_TEXT : entry.distanceText();
                    textWidths[index] = FontWidths.measure(font, text);
                    distanceWidth = Math.max(distanceWidth, textWidths[index]);
                }
                if (config.showDirection() && !showDistance && entry.spawn()) {
                    directionWidth = Math.max(directionWidth, FontWidths.measure(font, SPAWN_TEXT));
                }
            }
        }
        if (config.showGroupName()) {
            for (PlayerListGroup group : groups) {
                if (includes(group) && !content.entries(group).isEmpty()) {
                    nameWidth = Math.max(nameWidth,
                            FontWidths.measure(font, groupNames[group.ordinal()]));
                }
            }
        }
        lineHeight = renderer.fontHeight() + 2;
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
        for (PlayerListGroup group : groups) {
            if (!includes(group)) {
                continue;
            }
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
        width = hasGroup ? width : 0;
        height = hasGroup ? Math.max(lineHeight, height) : 0;
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
        String regularOne = "§5[§b§l120§5] §7PlayerOne";
        String regularTwo = "§d[§f§l113§d] §7PlayerTwo";
        String friend = "§e[§f§l117§e] §aPlayerThree";
        String enemy = "§c[§d§l109§c] §7PlayerFour";
        String nick = config.nickMarker() ? "§f[§5§l98§f] §bPlayerFive §e§lN"
                : "§f[§5§l98§f] §bPlayerFive";
        String dark = "§4[§d§l104§4] §7PlayerSix";
        String bountyHunter = "§6[§f§l112§6] §bPlayerSeven";
        Map<PlayerListGroup, List<PlayerListEntry>> groups = new EnumMap<>(PlayerListGroup.class);
        groups.put(PlayerListGroup.REGULARITY,
                Arrays.asList(
                        new PlayerListEntry(null, 0, regularOne,
                                sampleEnchantments(new PitEnchantment[] {PitEnchantment.Regularity,
                                        PitEnchantment.Gotta_go_fast}, 3, 3),
                                null, 45.0F, -135.0F, true, true, false),
                        new PlayerListEntry(null, 0, regularTwo,
                                sampleEnchantments(new PitEnchantment[] {PitEnchantment.Regularity,
                                        PitEnchantment.Gotta_go_fast, PitEnchantment.Solitude}, 3,
                                        3, 2),
                                null, 0.0F, 0.0F, false, false, true)));
        groups.put(PlayerListGroup.FRIEND,
                Collections.singletonList(new PlayerListEntry(null, 0, friend,
                        sampleEnchantments(
                                new PitEnchantment[] {PitEnchantment.Sweaty, PitEnchantment.Mirror},
                                3, 1),
                        null, 12.0F, 20.0F, true, true, false)));
        groups.put(PlayerListGroup.ENEMY,
                Collections.singletonList(new PlayerListEntry(null, 0, enemy,
                        sampleEnchantments(new PitEnchantment[] {PitEnchantment.Regularity,
                                PitEnchantment.Peroxide}, 3, 3),
                        null, 28.0F, -60.0F, true, true, false)));
        groups.put(PlayerListGroup.NICK,
                Collections.singletonList(new PlayerListEntry(null, 0, nick,
                        sampleEnchantments(new PitEnchantment[] {PitEnchantment.Somber,
                                PitEnchantment.Combo_Venom}, 1, 1),
                        null, 0.0F, 0.0F, false, false, true)));
        groups.put(PlayerListGroup.DARK,
                Collections.singletonList(new PlayerListEntry(null, 0, dark,
                        sampleEnchantments(new PitEnchantment[] {PitEnchantment.Somber,
                                PitEnchantment.Mind_Assault}, 1, 1),
                        null, 18.0F, 90.0F, true, true, false)));
        groups.put(PlayerListGroup.BOUNTY_HUNTER,
                Collections.singletonList(new PlayerListEntry(null, 0, bountyHunter, null, null,
                        0.0F, 0.0F, false, false, false)));
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
                config.enchantmentFormat(), config.showGem());
    }
}
