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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.EnumChatFormatting;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.languages.Languages;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback.Tone;

final class SwapController {
    private final Minecraft minecraft;
    private final Languages language;
    private final ClientSession session;
    private final SwapConfig config;
    private final BindingBook bindings;
    private final BiConsumer<Tone, String> report;
    private final Runnable lockInput;
    private final Consumer<Boolean> releaseInput;
    private final Deque<Request> queue = new ArrayDeque<>();
    private final Set<Object> accepted = new HashSet<>();
    private final Deque<Action> actions = new ArrayDeque<>();
    private final Set<String> problems = new LinkedHashSet<>();
    private final Set<Short> pendingClicks = new HashSet<>();
    private SwapConfig.Options options;
    private EntityPlayerSP player;
    private long sessionRevision;
    private long tick;
    private long lastClickTick;
    private long closeAt = -1;
    private boolean clicked;
    private GuiInventory screen;
    private Request current;
    private int groupWorkspace;
    private boolean cancelling;
    private boolean restoreOnCancel;
    private boolean failed;
    private boolean sendingClick;
    private boolean waitingToResume;
    private String rejection;

    SwapController(Minecraft minecraft, ClientSession session, SwapConfig config,
            BindingBook bindings, BiConsumer<Tone, String> report, Runnable lockInput,
            Consumer<Boolean> releaseInput, Languages language) {
        this.language = language;
        this.minecraft = minecraft;
        this.session = session;
        this.config = config;
        this.bindings = bindings;
        this.report = report;
        this.lockInput = lockInput;
        this.releaseInput = releaseInput;
    }

    boolean acceptsInput() {
        return minecraft.thePlayer != null && minecraft.theWorld != null
                && !minecraft.thePlayer.isSpectator()
                && minecraft.thePlayer.openContainer == minecraft.thePlayer.inventoryContainer
                && minecraft.thePlayer.inventory.getItemStack() == null
                && (minecraft.currentScreen == null || owns(minecraft.currentScreen));
    }

    boolean idle() {
        return options == null;
    }

    boolean owns(GuiScreen candidate) {
        return screen != null && screen == candidate;
    }

    boolean hidden() {
        return screen != null && !options.visible;
    }

    int unequipKey() {
        return options == null ? config.unequipKey.get() : options.unequipKey;
    }

    boolean rightClickEnabled() {
        return options == null ? config.rightClick.get() : options.rightClick;
    }

    void enqueueKey(int key) {
        if (!acceptsInput())
            return;
        for (SwapBinding binding : bindings.forKey(key)) {
            if (!binding.identity.matches(minecraft.thePlayer.inventoryContainer
                    .getSlot(binding.guiTarget()).getStack())) {
                enqueue(new Request(key, null), key);
                return;
            }
        }
    }

    void enqueueUnequip() {
        enqueue(new Request(-1, null), -1);
    }

    void enqueueArmor(SwapBinding binding) {
        enqueue(new Request(0, binding), binding.identity);
    }

    boolean enqueueAutomatic(List<Target> targets, String reason, BooleanSupplier ready) {
        if (!idle() || !acceptsInput() || !ready.getAsBoolean())
            return false;
        enqueue(new Request(0, null, targets, 0, reason, ready), targets);
        return true;
    }

    boolean enqueueAutomaticUnequip(int slot, String reason, BooleanSupplier ready) {
        if (!idle() || !acceptsInput() || !ready.getAsBoolean())
            return false;
        enqueue(new Request(0, null, null, slot, reason, ready), slot);
        return true;
    }

    private void enqueue(Request request, Object identity) {
        if (cancelling || waitingToResume || !acceptsInput() || !accepted.add(identity))
            return;
        if (options == null) {
            options = new SwapConfig.Options(config);
            player = minecraft.thePlayer;
            sessionRevision = session.revision();
            lockInput.run();
        }
        queue.addLast(request);
        closeAt = -1;
    }

    void tick() {
        tick++;
        if (options == null)
            return;
        try {
            advance();
        } catch (RuntimeException failure) {
            fail(failure.getMessage() == null ? language.translate("Unexpected error")
                    : failure.getMessage());
            if (options != null && !waitingToResume)
                finishQueue();
        }
    }

    private void advance() {
        if (rejection != null)
            throw new IllegalStateException(rejection);
        if (waitingToResume) {
            finish(false);
            return;
        }
        if (session.revision() != sessionRevision || minecraft.thePlayer != player) {
            finish(false);
            return;
        }
        if (!failed && (!acceptsInput() || screen != null && minecraft.currentScreen != screen)) {
            throw new IllegalStateException(language.translate("Inventory changed"));
        }
        if (cancelling) {
            finishCancellation();
            return;
        }
        while (options != null && !cancelling) {
            if (current == null) {
                if (queue.isEmpty()) {
                    finishQueue();
                    return;
                }
                current = queue.removeFirst();
                prepare();
            }
            // Readiness can change during the open delay. Once started, finish the transfer.
            if (current.ready != null && !current.started && !current.ready.getAsBoolean()) {
                cancel();
                return;
            }
            if (actions.isEmpty()) {
                reportProblems();
                current = null;
                continue;
            }
            Action action = actions.peekFirst();
            if (action.stage == 0 && action.binding != null
                    && action.binding.identity.matches(stack(action.binding.guiTarget()))) {
                actions.removeFirst();
                continue;
            }
            if (screen == null) {
                player.sendQueue.addToSendQueue(new C16PacketClientStatus(
                        C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                screen = new GuiInventory(player);
                minecraft.displayGuiScreen(screen);
                if (options == null || cancelling)
                    return;
                if (!owns(minecraft.currentScreen) || !acceptsInput()) {
                    throw new IllegalStateException(language.translate("Inventory did not open"));
                }
                lastClickTick = tick;
            }
            int delay = current.key == 0 ? options.swapDelay : options.bindingDelay;
            if (!clicked) {
                delay = options.openDelay;
            }
            if (tick - lastClickTick < delay)
                return;
            action.step();
            if (options == null || cancelling)
                return;
            if (action.done)
                actions.removeFirst();
        }
    }

    void clickSent(int windowId, short actionNumber) {
        if (!sendingClick || windowId != player.inventoryContainer.windowId)
            return;
        pendingClicks.add(actionNumber);
    }

    void confirmClick(int windowId, short actionNumber, boolean accepted) {
        if (options == null || player != minecraft.thePlayer
                || windowId != player.inventoryContainer.windowId
                || !pendingClicks.remove(actionNumber))
            return;
        if (!accepted)
            rejection = language.translate("Server rejected the inventory click");
    }

    private void prepare() {
        problems.clear();
        if (current.unequipSlot != 0) {
            if (stack(current.unequipSlot) != null)
                actions.addLast(new Action(null, current.unequipSlot));
            return;
        }
        if (current.key == -1) {
            for (int slot = 5; slot <= 8; slot++) {
                if (stack(slot) != null)
                    actions.addLast(new Action(null, slot));
            }
            return;
        }
        List<SwapBinding> candidates;
        if (current.automatic != null) {
            candidates = new ArrayList<>();
            for (Target target : current.automatic)
                candidates.add(target.binding);
        } else
            candidates = current.direct == null ? bindings.forKey(current.key)
                    : Collections.singletonList(current.direct);
        LinkedHashMap<ItemIdentity, Integer> locations = new LinkedHashMap<>();
        for (int slot = 36; slot <= 44; slot++)
            addLocation(locations, slot);
        for (int slot = 9; slot <= 35; slot++)
            addLocation(locations, slot);
        for (int slot = 5; slot <= 8; slot++)
            addLocation(locations, slot);
        if (current.automatic != null) {
            // Keep the selected source when identical enchantments have different Lives.
            for (Target target : current.automatic)
                if (target.binding.identity.matches(stack(target.source)))
                    locations.put(target.binding.identity, target.source);
        }
        LinkedHashMap<Integer, SwapBinding> selected = new LinkedHashMap<>();
        LinkedHashMap<Integer, SwapBinding> satisfiedSlots = new LinkedHashMap<>();
        for (SwapBinding binding : candidates) {
            if (binding.identity.matches(stack(binding.guiTarget()))) {
                if (!binding.equipment)
                    satisfiedSlots.putIfAbsent(binding.guiTarget(), binding);
                continue;
            }
            if (!locations.containsKey(binding.identity)) {
                problems.add(language.format("Missing item: {0}", binding.name));
            } else if (!selected.containsKey(binding.guiTarget())) {
                selected.put(binding.guiTarget(), binding);
            }
        }
        // Armor swaps can move an item out of an already satisfied slot.
        for (SwapBinding binding : satisfiedSlots.values())
            selected.putIfAbsent(binding.guiTarget(), binding);
        List<Action> ordered = new ArrayList<>();
        for (SwapBinding binding : selected.values()) {
            Action action = new Action(binding, 0);
            action.source = locations.get(binding.identity);
            ordered.add(action);
        }
        ordered.sort(Comparator.comparingInt(
                (Action action) -> action.binding.equipment ? (action.source >= 36 ? 0 : 1) : 2)
                .thenComparingInt(action -> action.binding.guiTarget()));
        groupWorkspace = workspace();
        actions.addAll(ordered);
    }

    private void addLocation(LinkedHashMap<ItemIdentity, Integer> locations, int slot) {
        ItemIdentity identity = ItemIdentity.read(stack(slot));
        if (identity != null)
            locations.putIfAbsent(identity, slot);
    }

    private int find(ItemIdentity identity) {
        for (int slot = 36; slot <= 44; slot++)
            if (identity.matches(stack(slot)))
                return slot;
        for (int slot = 9; slot <= 35; slot++)
            if (identity.matches(stack(slot)))
                return slot;
        for (int slot = 5; slot <= 8; slot++)
            if (identity.matches(stack(slot)))
                return slot;
        return -1;
    }

    private ItemStack stack(int slot) {
        return player.inventoryContainer.getSlot(slot).getStack();
    }

    private int workspace() {
        if (options.preferEmpty) {
            for (int hotbar = 0; hotbar < 9; hotbar++)
                if (stack(36 + hotbar) == null)
                    return hotbar;
        }
        return options.workspace;
    }

    private void click(int slot, int button, int mode) {
        if (!owns(minecraft.currentScreen) || player.openContainer != player.inventoryContainer
                || player.inventory.getItemStack() != null) {
            throw new IllegalStateException(language.translate("Inventory changed"));
        }
        sendingClick = true;
        try {
            minecraft.playerController.windowClick(player.inventoryContainer.windowId, slot, button,
                    mode, player);
        } finally {
            sendingClick = false;
        }
        current.started = true;
        clicked = true;
        lastClickTick = tick;
    }

    private String swapMessage(int first, int second) {
        if (current.automaticReason != null) {
            return options.automaticMessages
                    ? language.format("Swapped {0} with {1} due to {2}", itemName(first),
                            itemName(second), current.automaticReason)
                    : null;
        }
        return options.messages
                ? language.format("Swapped {0} with {1}", itemName(first), itemName(second))
                : null;
    }

    private String itemName(int slot) {
        ItemStack item = stack(slot);
        if (item != null) {
            String enchantments = PitEnchantmentReader.read(item).formatBoldDisplayNames();
            return (enchantments == null ? item.getDisplayName() : enchantments)
                    + EnumChatFormatting.RESET + EnumChatFormatting.GREEN;
        }
        switch (slot) {
            case 5:
                return language.translate("Helmet slot");
            case 6:
                return language.translate("Chestplate slot");
            case 7:
                return language.translate("Leggings slot");
            case 8:
                return language.translate("Boots slot");
            default:
                return slot >= 36 ? language.format("Hotbar slot {0}", slot - 35)
                        : language.format("Inventory slot {0}", slot - 8);
        }
    }

    private void reportProblems() {
        boolean automatic = current.automaticReason != null;
        if (automatic && !options.automaticMessages)
            return;
        for (String problem : problems)
            report.accept(Tone.WARNING,
                    automatic ? language.format("Automatic swap: {0}", problem) : problem);
    }

    void cancel() {
        if (options == null || cancelling)
            return;
        cancelling = true;
        restoreOnCancel = true;
        queue.clear();
        closeAt = -1;
    }

    private void fail(String message) {
        if (options == null || failed)
            return;
        cancel();
        failed = true;
        restoreOnCancel = false;
        pendingClicks.clear();
        rejection = null;
        waitingToResume = false;
        closeAt = tick + options.closeDelay;
        report.accept(Tone.ERROR, language.format("Swap failed: {0}", message));
    }

    private void finishCancellation() {
        if (restoreOnCancel) {
            Action action = actions.peekFirst();
            if (action != null && options.restore && action.restorable()) {
                int delay = current.key == 0 ? options.swapDelay : options.bindingDelay;
                if (tick - lastClickTick < delay)
                    return;
                action.swap(action.source, action.work);
            }
            restoreOnCancel = false;
        }
        finishQueue();
    }

    private void finishQueue() {
        if (closeAt < 0)
            closeAt = tick + options.closeDelay;
        if (!failed && screen == null || tick >= closeAt)
            finish(true);
    }

    void finish(boolean allowDelay) {
        boolean closingInventory = owns(minecraft.currentScreen);
        try {
            if (closingInventory) {
                try {
                    if (session.revision() == sessionRevision && player == minecraft.thePlayer
                            && minecraft.theWorld != null
                            && player.openContainer == player.inventoryContainer) {
                        player.closeScreen();
                    } else {
                        minecraft.displayGuiScreen(null);
                    }
                } finally {
                    if (owns(minecraft.currentScreen)) {
                        // GUI listeners can cancel closing. Detach our screen before restoring input.
                        minecraft.currentScreen = null;
                        if (minecraft.thePlayer != null && minecraft.theWorld != null)
                            minecraft.setIngameFocus();
                    }
                }
            }
        } catch (RuntimeException failure) {
            fail(language.translate("Inventory could not be closed"));
        } finally {
            if (owns(minecraft.currentScreen))
                minecraft.currentScreen = null;
            if (!allowDelay || !failed || closeAt <= tick) {
                if (allowDelay && options != null && options.resumeInputNextTick
                        && (failed || closingInventory && minecraft.currentScreen == null)) {
                    waitingToResume = true;
                } else {
                    reset();
                }
            }
        }
    }

    private void reset() {
        boolean active = options != null;
        boolean resume =
                active && session.revision() == sessionRevision && player == minecraft.thePlayer
                        && minecraft.theWorld != null && minecraft.currentScreen == null
                        && player.openContainer == player.inventoryContainer
                        && player.inventory.getItemStack() == null;
        queue.clear();
        accepted.clear();
        actions.clear();
        problems.clear();
        pendingClicks.clear();
        options = null;
        player = null;
        screen = null;
        current = null;
        clicked = false;
        closeAt = -1;
        cancelling = false;
        restoreOnCancel = false;
        failed = false;
        sendingClick = false;
        waitingToResume = false;
        rejection = null;
        if (active)
            releaseInput.accept(resume);
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private final class Action {
        final SwapBinding binding;
        final int unequipSlot;
        int stage;
        int source = -1;
        int work;
        ItemStack originalWorkspace;
        ItemStack expectedWorkspace;
        String message;
        boolean done;

        Action(SwapBinding binding, int unequipSlot) {
            this.binding = binding;
            this.unequipSlot = unequipSlot;
        }

        void step() {
            if (binding == null) {
                ItemStack armor = stack(unequipSlot);
                if (armor != null && player.inventory.getFirstEmptyStack() >= 0) {
                    // Armor shift clicks fill the main inventory before the hotbar.
                    int target = 9;
                    while (stack(target) != null)
                        target++;
                    message = swapMessage(unequipSlot, target);
                    click(unequipSlot, 0, 1);
                    if (stack(unequipSlot) == null) {
                        complete();
                        return;
                    }
                    problems.add(language.translate("Armor could not be moved to the inventory"));
                } else if (armor != null)
                    problems.add(language.translate("Not enough inventory space to unequip armor"));
                done = true;
                return;
            }
            if (stage == 1) {
                if (!binding.identity.matches(stack(36 + work))
                        || !ItemStack.areItemStacksEqual(stack(source), originalWorkspace)) {
                    throw new IllegalStateException(language.translate("Transfer slot changed"));
                }
                swap(binding.guiTarget(), work);
                if (!binding.identity.matches(stack(binding.guiTarget()))) {
                    throw new IllegalStateException(
                            language.translate("Armor could not be equipped"));
                }
                expectedWorkspace = copy(stack(36 + work));
                stage = 2;
                if (!options.restore)
                    complete();
                return;
            }
            if (stage == 2) {
                if (!restorable())
                    throw new IllegalStateException(language.translate("Transfer slot changed"));
                swap(source, work);
                complete();
                return;
            }
            if (!binding.identity.matches(stack(source)))
                source = find(binding.identity);
            if (source < 0) {
                problems.add(language.format("Missing item: {0}", binding.name));
                done = true;
                return;
            }
            message = swapMessage(binding.guiTarget(), source);
            if (binding.equipment) {
                if (!player.inventoryContainer.getSlot(binding.guiTarget())
                        .isItemValid(stack(source))) {
                    problems.add(language.format("Item does not fit: {0}", binding.name));
                    done = true;
                } else if (source >= 36) {
                    swap(binding.guiTarget(), source - 36);
                    verifyAndComplete();
                } else {
                    work = groupWorkspace;
                    originalWorkspace = copy(stack(36 + work));
                    swap(source, work);
                    expectedWorkspace = copy(stack(36 + work));
                    stage = 1;
                }
            } else {
                Slot sourceSlot = player.inventoryContainer.getSlot(source);
                ItemStack targetItem = stack(binding.guiTarget());
                if (source < 9 && targetItem != null && !sourceSlot.isItemValid(targetItem)
                        && player.inventory.getFirstEmptyStack() < 0) {
                    problems.add(
                            language.format("Not enough inventory space for {0}", binding.name));
                    done = true;
                } else {
                    swap(source, binding.target - 1);
                    verifyAndComplete();
                }
            }
        }

        private void swap(int slot, int hotbar) {
            click(slot, hotbar, 2);
        }

        private boolean restorable() {
            return stage > 0 && ItemStack.areItemStacksEqual(stack(source), originalWorkspace)
                    && ItemStack.areItemStacksEqual(stack(36 + work), expectedWorkspace);
        }

        private void verifyAndComplete() {
            if (!binding.identity.matches(stack(binding.guiTarget()))) {
                throw new IllegalStateException(
                        language.format("Item could not be swapped: {0}", binding.name));
            }
            complete();
        }

        private void complete() {
            done = true;
            if (message != null)
                report.accept(Tone.SUCCESS, message);
        }
    }
    private static final class Request {
        final int key;
        final SwapBinding direct;
        final List<Target> automatic;
        final int unequipSlot;
        final String automaticReason;
        final BooleanSupplier ready;
        boolean started;

        Request(int key, SwapBinding direct) {
            this(key, direct, null, 0, null, null);
        }

        Request(int key, SwapBinding direct, List<Target> automatic, int unequipSlot,
                String automaticReason, BooleanSupplier ready) {
            this.key = key;
            this.direct = direct;
            this.automatic = automatic;
            this.unequipSlot = unequipSlot;
            this.automaticReason = automaticReason;
            this.ready = ready;
        }
    }
    static final class Target {
        final SwapBinding binding;
        final int source;

        Target(int source, ItemStack stack, int hotbarTarget) {
            binding = SwapBinding.create(1, stack, hotbarTarget);
            this.source = source;
        }
    }
}
