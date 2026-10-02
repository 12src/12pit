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
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import pit12.runtime.session.ClientSession;

final class SwapController {
    private final Minecraft minecraft;
    private final ClientSession session;
    private final SwapConfig config;
    private final BindingBook bindings;
    private final Consumer<String> report;
    private final Runnable lockInput;
    private final Consumer<Boolean> releaseInput;
    private final Deque<Request> queue = new ArrayDeque<>();
    private final Set<Object> accepted = new HashSet<>();
    private final Deque<Action> actions = new ArrayDeque<>();
    private final Set<String> problems = new LinkedHashSet<>();
    private final List<String> completed = new ArrayList<>();
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

    SwapController(Minecraft minecraft, ClientSession session, SwapConfig config,
            BindingBook bindings, Consumer<String> report, Runnable lockInput,
            Consumer<Boolean> releaseInput) {
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
        return screen != null && options != null && !options.visible;
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
                enqueue(new Request(key, null), Integer.valueOf(key));
                return;
            }
        }
    }

    void enqueueUnequip() {
        enqueue(new Request(-1, null), Integer.valueOf(-1));
    }

    void enqueueArmor(SwapBinding binding) {
        enqueue(new Request(0, binding), binding.identity);
    }

    boolean enqueueAutomatic(List<Target> targets, BooleanSupplier ready) {
        if (!idle() || !acceptsInput() || targets.isEmpty() || !ready.getAsBoolean())
            return false;
        enqueue(new Request(0, null, new ArrayList<>(targets), 0, ready), targets);
        return true;
    }

    boolean enqueueAutomaticUnequip(int slot, BooleanSupplier ready) {
        if (!idle() || !acceptsInput() || slot < 5 || slot > 8 || !ready.getAsBoolean())
            return false;
        enqueue(new Request(0, null, null, slot, ready), Integer.valueOf(slot));
        return true;
    }

    private void enqueue(Request request, Object identity) {
        if (!acceptsInput() || !accepted.add(identity))
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
        if (session.revision() != sessionRevision || minecraft.thePlayer != player) {
            abandon();
            return;
        }
        if (!acceptsInput()) {
            cancel();
            return;
        }
        if (screen != null && minecraft.currentScreen != screen) {
            cancel();
            return;
        }
        try {
            while (options != null) {
                if (current == null) {
                    if (queue.isEmpty()) {
                        if (closeAt < 0)
                            closeAt = tick + options.closeDelay;
                        if (screen == null || tick >= closeAt)
                            finish();
                        return;
                    }
                    current = queue.removeFirst();
                    prepare();
                }
                // Recheck delayed automatic requests before their first click, then finish the transfer safely.
                if (current.ready != null && !current.started && !current.ready.getAsBoolean()) {
                    cancel();
                    return;
                }
                if (actions.isEmpty()) {
                    reportGroup();
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
                    screen = new GuiInventory(player);
                    minecraft.displayGuiScreen(screen);
                    if (options == null)
                        return;
                    if (!owns(minecraft.currentScreen) || !acceptsInput()) {
                        throw new IllegalStateException("Inventory did not open; swap stopped");
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
                if (action.done)
                    actions.removeFirst();
            }
        } catch (RuntimeException failure) {
            report.accept(failure.getMessage() == null ? "Swap stopped" : failure.getMessage());
            cancel();
        }
    }

    private void prepare() {
        problems.clear();
        completed.clear();
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
                    : java.util.Collections.singletonList(current.direct);
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
                problems.add("Missing item: " + binding.name);
            } else if (!selected.containsKey(binding.guiTarget())) {
                selected.put(binding.guiTarget(), binding);
            }
        }
        // A workspace can displace a satisfied slot, so check its final position after armor swaps.
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
            throw new IllegalStateException("Inventory changed; swap stopped");
        }
        minecraft.playerController.windowClick(player.inventoryContainer.windowId, slot, button,
                mode, player);
        if (current != null)
            current.started = true;
        clicked = true;
        lastClickTick = tick;
    }

    private void reportGroup() {
        for (String problem : problems)
            report.accept(problem);
        if (options.messages && !completed.isEmpty()) {
            report.accept(options.details ? "Swapped: " + String.join(", ", completed)
                    : "Swapped " + completed.size() + " item(s)");
        }
    }

    void cancel() {
        if (options == null)
            return;
        if (session.revision() == sessionRevision && player == minecraft.thePlayer
                && minecraft.theWorld != null && owns(minecraft.currentScreen)
                && player.openContainer == player.inventoryContainer
                && player.inventory.getItemStack() == null) {
            Action action = actions.peekFirst();
            if (action != null)
                action.restoreOnCancel();
        }
        finish();
    }

    void abandon() {
        reset();
    }

    private void finish() {
        if (session.revision() == sessionRevision && player == minecraft.thePlayer
                && minecraft.theWorld != null && owns(minecraft.currentScreen)
                && player.openContainer == player.inventoryContainer
                && player.inventory.getItemStack() == null) {
            try {
                player.closeScreen();
            } catch (RuntimeException failure) {
                report.accept("Inventory could not be closed");
            }
        }
        reset();
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
        completed.clear();
        options = null;
        player = null;
        screen = null;
        current = null;
        clicked = false;
        closeAt = -1;
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
        boolean done;

        Action(SwapBinding binding, int unequipSlot) {
            this.binding = binding;
            this.unequipSlot = unequipSlot;
        }

        void step() {
            if (binding == null) {
                ItemStack armor = stack(unequipSlot);
                if (armor != null && player.inventory.getFirstEmptyStack() >= 0) {
                    String name = armor.getDisplayName();
                    click(unequipSlot, 0, 1);
                    if (stack(unequipSlot) == null)
                        completed.add(name);
                    else
                        problems.add("Armor could not be moved to the inventory");
                } else if (armor != null)
                    problems.add("Not enough inventory space to unequip armor");
                done = true;
                return;
            }
            if (stage == 1) {
                if (!binding.identity.matches(stack(36 + work))
                        || !ItemStack.areItemStacksEqual(stack(source), originalWorkspace)) {
                    throw new IllegalStateException("Transfer slot changed; swap stopped");
                }
                swap(binding.guiTarget(), work);
                if (!binding.identity.matches(stack(binding.guiTarget()))) {
                    throw new IllegalStateException("Armor could not be equipped");
                }
                expectedWorkspace = copy(stack(36 + work));
                stage = 2;
                if (!options.restore)
                    complete();
                return;
            }
            if (stage == 2) {
                if (!restorable())
                    throw new IllegalStateException("Transfer slot changed; swap stopped");
                swap(source, work);
                complete();
                return;
            }
            if (source < 5 || !binding.identity.matches(stack(source)))
                source = find(binding.identity);
            if (source < 0) {
                problems.add("Missing item: " + binding.name);
                done = true;
                return;
            }
            if (binding.equipment) {
                if (!player.inventoryContainer.getSlot(binding.guiTarget())
                        .isItemValid(stack(source))) {
                    problems.add("Item does not fit: " + binding.name);
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
                    problems.add("Not enough inventory space for " + binding.name);
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

        void restoreOnCancel() {
            if (binding != null && options.restore && restorable()) {
                try {
                    swap(source, work);
                } catch (RuntimeException ignored) {
                }
            }
        }

        private void verifyAndComplete() {
            if (!binding.identity.matches(stack(binding.guiTarget()))) {
                throw new IllegalStateException("Item could not be swapped: " + binding.name);
            }
            complete();
        }

        private void complete() {
            completed.add(binding.display(options.details));
            done = true;
        }
    }
    private static final class Request {
        final int key;
        final SwapBinding direct;
        final List<Target> automatic;
        final int unequipSlot;
        final BooleanSupplier ready;
        boolean started;

        Request(int key, SwapBinding direct) {
            this(key, direct, null, 0, null);
        }

        Request(int key, SwapBinding direct, List<Target> automatic, int unequipSlot,
                BooleanSupplier ready) {
            this.key = key;
            this.direct = direct;
            this.automatic = automatic;
            this.unequipSlot = unequipSlot;
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
