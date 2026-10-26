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

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import pit12.feature.relation.api.RelationLookup;
import pit12.feature.swap.api.SwapBindings;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputHandler;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;
import pit12.runtime.languages.Languages;
import pit12.runtime.pit.PitContext;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Attack;
import pit12.shared.event.ClientEvents.ScreenDraw;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;

public final class SwapFeature
        implements ClientLifecycle, ConfigChangeListener, SwapHooks, InputHandler {
    private final Runnable onTickListener = this::onTick;
    private final Consumer<Attack> onAttackListener = this::onAttack;
    private final Consumer<ScreenDraw> onDrawListener = this::onDraw;

    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final SwapConfig config;
    private final CommandRegistry commands;
    private final ClientSession session;
    private final BindingBook bindings;
    private final SwapController controller;
    private final AutoSwapController automatic;
    private final SwapOverlay overlay;
    private final SwapHooksBinding hookBinding;
    private final InputBlocker input;
    private final Runnable sessionListener = this::worldChanged;
    private boolean started;
    private boolean listening;
    private boolean rightClickHeld;
    private boolean mouseGrabbed;

    public SwapFeature(Minecraft minecraft, ClientThread client, ConfigCatalog configs,
            SwapConfig config, ClientSession session, PitContext pit,
            PlayerEquipmentAccess equipment, RelationLookup relations, CommandRegistry commands,
            Path path, SwapHooksBinding hookBinding, InputBlocker input, Languages language) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.commands = commands;
        this.session = session;
        this.hookBinding = hookBinding;
        this.input = input;
        bindings = new BindingBook(client, path,
                message -> report(Tone.ERROR, language.translate(message)));
        controller = new SwapController(minecraft, session, config, bindings, this::report, input,
                this, language);
        DarkTargets darkTargets = new DarkTargets(minecraft, config, equipment, relations);
        automatic =
                new AutoSwapController(minecraft, config, controller, pit, darkTargets, language);
        overlay = new SwapOverlay(minecraft, bindings);
        commands.register(
                new SwapCommand(minecraft, bindings, config, automatic, language).definition(),
                config, true);
    }

    public SwapBindings bindings() {
        return bindings;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        configs.addListener(this);
        bindings.start();
        updateListening();
        commands.setActive(config, true);
    }

    @Override
    public void stop() {
        session.checkThread();
        if (!started)
            return;
        started = false;
        commands.setActive(config, false);
        controller.finish(false);
        configs.removeListener(this);
        updateListening();
        bindings.stop();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("swap", "enabled")) {
            if (!config.enabled()) {
                controller.cancel();
                automatic.reset();
                overlay.clear();
            }
            updateListening();
        }
        if (changes.affects("swap", "highlight_bindings") && !config.highlight.get())
            overlay.clear();
        automatic.onConfigChanged(changes);
    }

    private void updateListening() {
        boolean enabled = started && (config.enabled() || controller.busy());
        if (enabled == listening)
            return;
        listening = enabled;
        if (enabled) {
            session.addListener(sessionListener);
            ClientEvents.TICK_END.add(onTickListener);
            ClientEvents.ATTACK_LAST.add(onAttackListener);
            ClientEvents.GUI_DRAW.add(onDrawListener);
            hookBinding.pit12$bindSwapHooks(this);
            input.addHandler(this);
        } else {
            session.removeListener(sessionListener);
            input.removeHandler(this);
            hookBinding.pit12$bindSwapHooks(null);
            ClientEvents.TICK_END.remove(onTickListener);
            ClientEvents.ATTACK_LAST.remove(onAttackListener);
            ClientEvents.GUI_DRAW.remove(onDrawListener);
            automatic.reset();
            updateMouseGrab();
            overlay.clear();
            rightClickHeld = false;
        }
    }

    private void worldChanged() {
        controller.finish(false);
        automatic.reset();
        updateMouseGrab();
        overlay.clear();
        rightClickHeld = false;
        updateListening();
    }

    @Override
    public boolean consumesKey(int key) {
        return config.enabled()
                && (Keybind.matches(config.unequipKey.get(), key) || bindings.hasKey(key));
    }

    @Override
    public boolean key(int key, boolean pressed, boolean repeat) {
        if (!pressed || key <= 0 || key >= Keyboard.KEYBOARD_SIZE || !config.enabled()
                || controller.inputBlocked())
            return false;
        boolean unequip = Keybind.matches(controller.unequipKey(), key);
        if (!unequip && !bindings.hasKey(key))
            return false;
        if (!repeat) {
            if (unequip)
                controller.enqueueUnequip();
            else
                controller.enqueueKey(key);
        }
        return true;
    }

    @Override
    public boolean rightClick() {
        if (input.locked())
            return false;
        if (rightClickHeld)
            return true;
        if (!config.enabled() || !controller.rightClickEnabled() || minecraft.currentScreen != null
                || controller.inputBlocked() || minecraft.playerController.getIsHittingBlock())
            return false;
        ItemStack held = minecraft.thePlayer.getHeldItem();
        int target = SwapBinding.armorTarget(held);
        if (target == 0 || minecraft.thePlayer.inventory.armorInventory[target - 1] == null
                || PitEnchantmentReader.contains(held, PitEnchantment.Trash_Panda))
            return false;
        MovingObjectPosition hit = minecraft.objectMouseOver;
        // An entity interaction can succeed on the server even if the client thinks nothing happened.
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY)
            return false;
        controller.enqueueArmor(SwapBinding.create(1, held, 0));
        rightClickHeld = true;
        return true;
    }

    @Override
    public void drawBinding(ItemStack stack, int x, int y) {
        if (config.enabled() && config.highlight.get())
            overlay.draw(stack, x, y);
    }

    @Override
    public void sound(String name, double x, double y, double z, float volume, float pitch) {
        automatic.sound(name, x, y, z, volume, pitch);
    }

    @Override
    public void inventoryTick(int phase) {
        if (controller.tick(phase)) {
            updateMouseGrab();
            updateListening();
        }
    }

    private void onTick() {
        if (config.enabled()) {
            if (!input.locked()
                    && !InputBlocker.held(minecraft.gameSettings.keyBindUseItem.getKeyCode()))
                rightClickHeld = false;
            if (config.autoSwap.get() && !input.blockedFor(this))
                automatic.tick();
            if (config.highlight.get())
                overlay.refresh();
        }
        updateMouseGrab();
    }

    private void onAttack(Attack event) {
        if (!config.enabled() || !config.autoSwap.get() || !config.dark.get()
                || event.entityPlayer != minecraft.thePlayer
                || !(event.target instanceof EntityPlayer))
            return;
        session.checkThread();
        automatic.attack((EntityPlayer) event.target);
    }

    private void updateMouseGrab() {
        boolean hidden = controller.hidden() && controller.owns(minecraft.currentScreen);
        if (hidden) {
            if (!Mouse.isGrabbed())
                Mouse.setGrabbed(true);
        } else if (mouseGrabbed && minecraft.currentScreen != null && Mouse.isGrabbed()) {
            Mouse.setGrabbed(false);
        }
        mouseGrabbed = hidden;
    }

    private void onDraw(ScreenDraw event) {
        if (controller.owns(event.gui) && controller.hidden())
            event.canceled = true;
    }

    @Override
    public void clickSent(int windowId, short actionNumber) {
        controller.clickSent(windowId, actionNumber);
    }

    @Override
    public void confirmClick(int windowId, short actionNumber, boolean accepted) {
        controller.confirmClick(windowId, actionNumber, accepted);
    }

    private void report(Tone tone, String message) {
        if (minecraft.thePlayer != null)
            ChatFeedback.reply(minecraft.thePlayer, tone, message);
    }
}
