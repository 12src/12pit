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
package pit12.feature.swap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraftforge.common.util.Constants;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.SpawnState;

final class AutoSwapController {
    private final Minecraft minecraft;
    private final SwapConfig config;
    private final SwapController swaps;
    private final PitContext pit;
    private final EnumSet<PitEnchantment> used = EnumSet.noneOf(PitEnchantment.class);
    private EntityPlayerSP player;
    private long tick;
    private long retryAt;
    private boolean inSpawn;
    private boolean poisoned;
    private boolean venomPending;
    private boolean automaticPending;
    private Pending pending;
    private PitEnchantment activeType;
    private ItemIdentity activePants;
    private ItemStack originalPants;
    private boolean canRestore;

    AutoSwapController(Minecraft minecraft, SwapConfig config, SwapController swaps,
            PitContext pit) {
        this.minecraft = minecraft;
        this.config = config;
        this.swaps = swaps;
        this.pit = pit;
    }

    void tick() {
        if (!config.enabled() || !config.autoSwap.get()) {
            if (automaticPending)
                swaps.cancel();
            if (automaticPending || player != null)
                reset();
            return;
        }
        tick++;
        if (player != minecraft.thePlayer) {
            if (automaticPending)
                swaps.cancel();
            reset();
            player = minecraft.thePlayer;
        }
        if (!isActive(player)) {
            if (automaticPending)
                swaps.cancel();
            reset();
            return;
        }
        boolean nowInSpawn = pit.current().spawnStateAt(player.posX, player.posY,
                player.posZ) == SpawnState.IN_SPAWN;
        if (nowInSpawn && !inSpawn)
            used.clear();
        inSpawn = nowInSpawn;
        if (player.getHealth() <= 0) {
            if (automaticPending)
                swaps.cancel();
            return;
        }
        boolean nowPoisoned = player.isPotionActive(Potion.poison);
        if (nowPoisoned && !poisoned) {
            venomPending = true;
            retryAt = tick;
        } else if (!nowPoisoned) {
            venomPending = false;
        }
        poisoned = nowPoisoned;
        if (!swaps.idle()) {
            if (!poisoned || !automaticPending || pending == null)
                return;
            swaps.cancel();
            return;
        }
        if (automaticPending) {
            finishPending();
            if (poisoned && venomPending)
                retryAt = tick;
        }
        if (activePants != null && !activePants.matches(leggings()))
            clearActive();
        if (tick < retryAt || minecraft.currentScreen != null || !minecraft.inGameHasFocus
                || !swaps.acceptsInput())
            return;
        // Enchanted leggings cannot help while poisoned, so poison swaps take priority.
        if (poisoned) {
            if (venomPending)
                swapVenom();
            return;
        }
        if (activeType != null) {
            if (canRestore && config.restorePants.get() && shouldRestore()) {
                restore();
                return;
            }
            if (!used.contains(activeType))
                return;
        }
        PitEnchantment first = config.pantsPriority.get() == 0 ? PitEnchantment.Escape_Pod
                : PitEnchantment.Phoenix;
        PitEnchantment second = first == PitEnchantment.Escape_Pod ? PitEnchantment.Phoenix
                : PitEnchantment.Escape_Pod;
        if (!equip(first) && !equip(second) && (eligible(first) || eligible(second)))
            retryAt = tick + 10;
    }

    private boolean isActive(EntityPlayerSP candidate) {
        return config.enabled() && config.autoSwap.get() && candidate != null
                && candidate == minecraft.thePlayer && minecraft.theWorld != null
                && !candidate.isSpectator();
    }

    private boolean canSwap(EntityPlayerSP candidate) {
        return isActive(candidate) && candidate.getHealth() > 0;
    }

    private ItemStack leggings() {
        return player.inventory.armorInventory[1];
    }

    private void swapVenom() {
        if (config.skipVenomPants.get()
                && PitEnchantmentReader.contains(leggings(), PitEnchantment.Combo_Venom)) {
            venomPending = false;
            return;
        }
        List<SwapController.Target> targets = new ArrayList<>();
        if (config.venomArmor.get()) {
            addDiamond(targets, Items.diamond_leggings, 7, 0);
            addDiamond(targets, Items.diamond_boots, 8, 0);
        }
        if (config.venomSpade.get())
            addDiamond(targets, Items.diamond_shovel, 35 + config.spadeSlot.get(),
                    config.spadeSlot.get());
        if (targets.isEmpty()) {
            retryAt = tick + 10;
            return;
        }
        if (swaps.enqueueAutomatic(targets,
                () -> canSwap(player) && player.isPotionActive(Potion.poison)
                        && (!config.skipVenomPants.get() || !PitEnchantmentReader
                                .contains(leggings(), PitEnchantment.Combo_Venom)))) {
            automaticPending = true;
            venomPending = false;
        }
    }

    private void addDiamond(List<SwapController.Target> targets, Item item, int destination,
            int hotbarTarget) {
        ItemStack current = player.inventoryContainer.getSlot(destination).getStack();
        if (current != null && current.getItem() == item)
            return;
        for (int index = 0; index < 36; index++) {
            ItemStack stack = player.inventory.getStackInSlot(index);
            if (stack != null && stack.stackSize > 0 && stack.getItem() == item) {
                targets.add(new SwapController.Target(guiSlot(index), stack, hotbarTarget));
                return;
            }
        }
    }

    private boolean eligible(PitEnchantment type) {
        return !used.contains(type) && (type == PitEnchantment.Escape_Pod ? config.escapePod.get()
                : config.phoenix.get()) && player.getHealth() <= threshold(type);
    }

    private double threshold(PitEnchantment type) {
        return type == PitEnchantment.Escape_Pod ? config.podThreshold.get()
                : config.phoenixThreshold.get();
    }

    private boolean equip(PitEnchantment type) {
        if (!eligible(type))
            return false;
        ItemStack worn = leggings();
        if (PitEnchantmentReader.contains(worn, type)) {
            activeType = type;
            activePants = ItemIdentity.read(worn);
            return true;
        }
        int source = findPants(type);
        if (source < 0)
            return false;
        SwapController.Target target = new SwapController.Target(source,
                player.inventoryContainer.getSlot(source).getStack(), 0);
        ItemIdentity wornIdentity = ItemIdentity.read(worn);
        ItemStack previous = canRestore ? originalPants : worn == null ? null : worn.copy();
        if (swaps.enqueueAutomatic(Collections.singletonList(target),
                () -> canSwap(player) && !player.isPotionActive(Potion.poison) && eligible(type)
                        && (wornIdentity == null ? leggings() == null
                                : wornIdentity.matches(leggings())))) {
            pending = new Pending(type, target.binding.identity, previous, false);
            automaticPending = true;
        }
        return true;
    }

    private int findPants(PitEnchantment type) {
        int selected = -1;
        int mostLives = -1;
        for (int index = 0; index < 36; index++) {
            ItemStack stack = player.inventory.getStackInSlot(index);
            if (stack == null || stack.stackSize <= 0 || SwapBinding.armorTarget(stack) != 2
                    || !PitEnchantmentReader.contains(stack, type))
                continue;
            if (type != PitEnchantment.Phoenix || !config.highLives.get())
                return guiSlot(index);
            int lives = lives(stack);
            if (selected < 0 || lives > mostLives) {
                selected = guiSlot(index);
                mostLives = lives;
            }
        }
        return selected;
    }

    private static int lives(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey("ExtraAttributes", Constants.NBT.TAG_COMPOUND))
            return -1;
        NBTTagCompound extra = root.getCompoundTag("ExtraAttributes");
        // Missing numeric NBT reads as zero; keep unknown Lives below known values.
        return extra.hasKey("Lives", Constants.NBT.TAG_ANY_NUMERIC)
                ? Math.max(-1, extra.getInteger("Lives"))
                : -1;
    }

    private boolean shouldRestore() {
        if (config.pantsRestoreMode.get() == 1)
            return used.contains(activeType);
        return player.getHealth() >= config.pantsRestoreThreshold.get()
                && player.getHealth() > threshold(activeType);
    }

    private void restore() {
        if (originalPants == null) {
            if (player.inventory.getFirstEmptyStack() < 0) {
                retryAt = tick + 10;
                return;
            }
            if (swaps.enqueueAutomaticUnequip(7, this::readyToRestore)) {
                pending = new Pending(activeType, activePants, null, true);
                automaticPending = true;
            }
            return;
        }
        ItemStack expected = originalPants;
        int source = findOriginalPants(expected);
        int originalLives = lives(originalPants);
        if (source < 0 && originalLives >= 0) {
            expected = originalPants.copy();
            expected.getTagCompound().getCompoundTag("ExtraAttributes").setInteger("Lives",
                    originalLives - 1);
            source = findOriginalPants(expected);
        }
        if (source < 0) {
            // A manual move or missing original must never cause us to restore a different item.
            clearActive();
            return;
        }
        ItemStack restored = expected;
        SwapController.Target target = new SwapController.Target(source, restored, 0);
        if (swaps.enqueueAutomatic(Collections.singletonList(target),
                () -> readyToRestore() && ItemStack.areItemStacksEqual(restored,
                        player.inventoryContainer.getSlot(target.source).getStack()))) {
            pending = new Pending(activeType, activePants, restored, true);
            automaticPending = true;
        }
    }

    private int findOriginalPants(ItemStack expected) {
        for (int index = 0; index < 36; index++) {
            if (ItemStack.areItemStacksEqual(expected, player.inventory.getStackInSlot(index)))
                return guiSlot(index);
        }
        return -1;
    }

    private boolean readyToRestore() {
        return canSwap(player) && !player.isPotionActive(Potion.poison) && config.restorePants.get()
                && activePants != null && activePants.matches(leggings()) && shouldRestore();
    }

    private void finishPending() {
        automaticPending = false;
        if (pending == null)
            return;
        if (pending.restoring) {
            if (ItemStack.areItemStacksEqual(pending.previous, leggings()))
                clearActive();
            else
                retryAt = tick + 10;
        } else if (pending.equipped.matches(leggings())) {
            activeType = pending.type;
            activePants = pending.equipped;
            originalPants = pending.previous;
            canRestore = true;
        } else {
            retryAt = tick + 10;
        }
        pending = null;
    }

    void sound(String name, double x, double y, double z, float volume, float pitch) {
        if (!config.enabled() || !config.autoSwap.get())
            return;
        if (name.startsWith("minecraft:"))
            name = name.substring(10);
        if (!"note.pling".equals(name) && !"random.explode".equals(name)
                && !"mob.bat.death".equals(name))
            return;
        EntityPlayerSP current = minecraft.thePlayer;
        if (!isActive(current))
            return;
        if (player != current) {
            reset();
            player = current;
        }
        // These server sound signatures report enchantment use and rearming, even with audio muted.
        if ("note.pling".equals(name) && near(volume, 8) && near(pitch, 4.05f)) {
            if (automaticPending)
                swaps.cancel();
            reset();
            player = current;
            return;
        }
        if (Math.abs(current.posX - x) > 1 || Math.abs(current.posY - y) > 1
                || Math.abs(current.posZ - z) > 1 || !near(volume, 1))
            return;
        if ("random.explode".equals(name) && near(pitch, 0.89f)
                && PitEnchantmentReader.contains(leggings(), PitEnchantment.Escape_Pod))
            used.add(PitEnchantment.Escape_Pod);
        else if ("mob.bat.death".equals(name) && near(pitch, 0)
                && PitEnchantmentReader.contains(leggings(), PitEnchantment.Phoenix))
            used.add(PitEnchantment.Phoenix);
    }

    boolean used(PitEnchantment type) {
        return used.contains(type);
    }

    boolean active(PitEnchantment type) {
        return activeType == type;
    }

    void manualReset() {
        if (automaticPending)
            swaps.cancel();
        reset();
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 0.05f;
    }

    private static int guiSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private void clearActive() {
        activeType = null;
        activePants = null;
        originalPants = null;
        canRestore = false;
    }

    void reset() {
        player = null;
        retryAt = 0;
        inSpawn = false;
        poisoned = false;
        venomPending = false;
        automaticPending = false;
        pending = null;
        used.clear();
        clearActive();
    }

    private static final class Pending {
        final PitEnchantment type;
        final ItemIdentity equipped;
        final ItemStack previous;
        final boolean restoring;

        Pending(PitEnchantment type, ItemIdentity equipped, ItemStack previous, boolean restoring) {
            this.type = type;
            this.equipped = equipped;
            this.previous = previous;
            this.restoring = restoring;
        }
    }
}
