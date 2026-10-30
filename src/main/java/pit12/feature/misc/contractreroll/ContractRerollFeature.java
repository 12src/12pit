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
package pit12.feature.misc.contractreroll;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputHandler;
import pit12.runtime.languages.Languages;
import pit12.runtime.pit.PitContext;
import pit12.runtime.pit.PitState;
import pit12.runtime.scoreboard.SidebarCache;
import pit12.runtime.scoreboard.SidebarLine;
import pit12.runtime.scoreboard.SidebarListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.input.Keybind;
import pit12.shared.input.MinecraftActions;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.rendering.FontWidths;
import pit12.shared.text.TextCodes;

public final class ContractRerollFeature
        implements ClientLifecycle, InputHandler, SidebarListener, ConfigChangeListener {
    private static final Logger LOGGER = Logger.getLogger(ContractRerollFeature.class.getName());
    private static final int[] CHOICE_SLOTS = {11, 13, 15};
    private static final Pattern KILLS = Pattern.compile("Kill ([0-9]+) players\\.");
    private static final Pattern NO_ARMOR =
            Pattern.compile("Kill ([0-9]+) players without wearing any armor\\.");
    private static final Pattern FIST = Pattern.compile(
            "Get ([0-9]+) killing blows using your fist inside the small sea lantern circle of the pit\\.");
    private static final Pattern INGOTS = Pattern.compile("Collect ([0-9]+) ingots");
    private static final Pattern ARROWS =
            Pattern.compile("Land ([0-9]+) arrow shots on enemies\\.");
    private static final Pattern NO_PERKS =
            Pattern.compile("Kill ([0-9]+) players without any perks equipped\\.");
    private static final Pattern APPLES =
            Pattern.compile("Eat ([0-9]+) golden apples \\(no golden heads\\)\\.");
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final ContractRerollConfig config;
    private final ClientSession session;
    private final PitContext pit;
    private final SidebarCache sidebar;
    private final InputBlocker input;
    private final MinecraftActions actions;
    private final Languages language;
    private final Runnable sessionChanged = () -> finish(false);
    private final Runnable languageChanged = this::updateHint;
    private boolean started;
    private State state = State.IDLE;
    private EntityPlayerSP player;
    private boolean hasContract;
    private boolean previousPauseOnLostFocus;
    private GuiScreen previousScreen;
    private GuiChest menu;
    private ItemStack[] pendingStacks;
    private long deadline;
    private long clickAt;
    private String hint;

    public ContractRerollFeature(Minecraft minecraft, ConfigCatalog configs,
            ContractRerollConfig config, ClientSession session, PitContext pit,
            SidebarCache sidebar, InputBlocker input, MinecraftActions actions,
            Languages language) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.pit = pit;
        this.sidebar = sidebar;
        this.input = input;
        this.actions = actions;
        this.language = language;
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        input.addHandler(this);
        configs.addListener(this);
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        finish(false);
        input.removeHandler(this);
        configs.removeListener(this);
    }

    @Override
    public boolean consumesKey(int key) {
        return Keybind.matches(config.key.get(), key);
    }

    @Override
    public boolean blockedKey(int key, int modifiers) {
        if (Keybind.matches(config.key.get(), key, modifiers)) {
            finish(true);
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            input.releaseCursor();
            return true;
        }
        return false;
    }

    @Override
    public boolean key(int key, boolean pressed, boolean repeat) {
        if (!pressed || !consumesKey(key) || minecraft.currentScreen != null
                || !minecraft.inGameHasFocus || minecraft.thePlayer == null
                || minecraft.theWorld == null)
            return false;
        if (repeat || state != State.IDLE)
            return true;
        if (pit.current().pitState() != PitState.IN_PIT) {
            report(language.translate("Start rerolling contracts in the Pit."));
            return true;
        }
        try {
            player = minecraft.thePlayer;
            previousPauseOnLostFocus = minecraft.gameSettings.pauseOnLostFocus;
            minecraft.gameSettings.pauseOnLostFocus = false;
            enter(State.LANGUAGE, Minecraft.getSystemTime());
            session.addListener(sessionChanged);
            sidebar.addListener(this);
            language.addListener(languageChanged);
            refreshContract();
            MinecraftForge.EVENT_BUS.register(this);
            input.lock(this);
            clickAt = Minecraft.getSystemTime() + ThreadLocalRandom.current().nextInt(150, 351);
        } catch (RuntimeException failure) {
            failedAction(failure);
        }
        return true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == State.IDLE)
            return;
        if (player != minecraft.thePlayer || minecraft.theWorld == null || player.isDead) {
            finish(false);
            return;
        }
        if (pit.current().pitState() != PitState.IN_PIT) {
            fail(language.translate("You left the Pit."));
            return;
        }
        if (input.blockedFor(this)) {
            fail(language.translate("Another action took control."));
            return;
        }
        long now = Minecraft.getSystemTime();
        if (now >= deadline) {
            fail(state == State.QUESTS
                    ? language.translate(
                            "Move next to the Quest Master and aim at them before starting.")
                    : language.translate("Timed out waiting for the server."));
            return;
        }
        try {
            if (!checkScreen(minecraft.currentScreen))
                return;
            switch (state) {
                case LANGUAGE:
                    if (now >= clickAt) {
                        enter(State.LANGUAGE_REPLY, now);
                        player.sendChatMessage("/lang English");
                    }
                    break;
                case CONTRACT:
                    if (!hasContract) {
                        if (clickAt == 0)
                            clickAt = now + ThreadLocalRandom.current().nextInt(150, 351);
                        else if (now >= clickAt)
                            openQuests(now);
                    } else {
                        clickAt = 0;
                    }
                    break;
                case QUESTS:
                case PURCHASE:
                case START:
                    if (menu != null)
                        clickMenu(now);
                    break;
                case CHOICES:
                    if (menu != null)
                        pickContract(now);
                    break;
                case CONTRACT_START:
                    if (minecraft.currentScreen == null && hasContract)
                        enter(State.CONTRACT, now);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException failure) {
            failedAction(failure);
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (state == State.LANGUAGE_REPLY && event.type != 2
                && TextCodes.strip(event.message.getUnformattedText()).trim()
                        .equals("Selected language: English")) {
            refreshContract();
            enter(State.CONTRACT, Minecraft.getSystemTime());
        }
    }

    @Override
    public void onLineChanged(SidebarLine previous, SidebarLine current) {
        if (state != State.IDLE) {
            refreshContract();
            if (hasContract && state.title != null)
                fail(language.translate("A contract started unexpectedly."));
        }
    }

    private void refreshContract() {
        hasContract = false;
        for (SidebarLine line : sidebar.lines()) {
            String text = line.formattedText(minecraft.fontRendererObj);
            if (text.contains("Contract") || text.contains("合同")) {
                hasContract = true;
                break;
            }
        }
    }

    private void openQuests(long now) {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || hit.entityHit == null) {
            fail(language
                    .translate("Move next to the Quest Master and aim at them before starting."));
            return;
        }
        if (player.inventory.getItemStack() != null) {
            fail(language.translate("The inventory changed."));
            return;
        }
        enter(State.QUESTS, now);
        input.runAction(this, actions::pit12$rightClick);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (state == State.IDLE)
            return;
        if (event.gui == null) {
            if (menu != null)
                fail(language.translate("The menu closed unexpectedly."));
        } else if (event.gui != previousScreen && !acceptMenu(event.gui)) {
            fail(state == State.QUESTS
                    ? language.translate(
                            "Move next to the Quest Master and aim at them before starting.")
                    : language.translate("An unexpected menu opened."));
        }
    }

    private boolean checkScreen(GuiScreen screen) {
        if (menu == null && (screen == null || screen == previousScreen))
            return true;
        if (!acceptMenu(screen) || menu != null && menu != screen) {
            fail(language.translate("The menu changed unexpectedly."));
            return false;
        }
        menu = (GuiChest) screen;
        if (player.openContainer != menu.inventorySlots) {
            fail(language.translate("The inventory changed."));
            return false;
        }
        return true;
    }

    // noinspection BooleanMethodIsAlwaysInverted
    private boolean acceptMenu(GuiScreen screen) {
        if (state.title == null || !(screen instanceof GuiChest)
                || !(((GuiChest) screen).inventorySlots instanceof ContainerChest))
            return false;
        IInventory inventory =
                ((ContainerChest) ((GuiChest) screen).inventorySlots).getLowerChestInventory();
        String title = TextCodes.strip(inventory.getDisplayName().getUnformattedText());
        if (state == State.PURCHASE && inventory.getSizeInventory() == State.CHOICES.slots
                && State.CHOICES.title.equals(title))
            enter(State.CHOICES, Minecraft.getSystemTime());
        return inventory.getSizeInventory() == state.slots && state.title.equals(title);
    }

    private void clickMenu(long now) {
        int slot = state == State.QUESTS ? 32 : 11;
        ItemStack stack = menu.inventorySlots.getSlot(slot).getStack();
        if (state == State.QUESTS && stack == null) {
            slot = 31;
            stack = menu.inventorySlots.getSlot(slot).getStack();
        }
        if (stack == null) {
            clickAt = 0;
            pendingStacks = null;
            return;
        }
        if (pendingStacks == null || !ItemStack.areItemStacksEqual(pendingStacks[0], stack)) {
            pendingStacks = new ItemStack[] {stack.copy()};
            clickAt = now + ThreadLocalRandom.current().nextInt(150, 351);
            return;
        }
        if (now < clickAt)
            return;
        click(slot, state == State.QUESTS ? State.PURCHASE
                : state == State.PURCHASE ? State.CHOICES : State.CONTRACT_START, now);
    }

    private void pickContract(long now) {
        IInventory inventory = ((ContainerChest) menu.inventorySlots).getLowerChestInventory();
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (slot == 11 || slot == 13 || slot == 15) {
                if (stack != null && stack.getItem() == Items.enchanted_book)
                    continue;
            } else if (stack == null) {
                continue;
            }
            pendingStacks = null;
            clickAt = 0;
            return;
        }
        boolean changed = pendingStacks == null;
        if (!changed) {
            for (int index = 0; index < CHOICE_SLOTS.length; index++)
                changed |= !ItemStack.areItemStacksEqual(pendingStacks[index],
                        inventory.getStackInSlot(CHOICE_SLOTS[index]));
        }
        if (changed) {
            pendingStacks = new ItemStack[CHOICE_SLOTS.length];
            for (int index = 0; index < CHOICE_SLOTS.length; index++)
                pendingStacks[index] = inventory.getStackInSlot(CHOICE_SLOTS[index]).copy();
            clickAt = now + ThreadLocalRandom.current().nextInt(150, 351);
            return;
        }
        if (now < clickAt)
            return;
        String[] tooltips = new String[CHOICE_SLOTS.length];
        for (int index = 0; index < tooltips.length; index++) {
            tooltips[index] = TextCodes
                    .strip(String.join(" ", pendingStacks[index].getTooltip(player, false)))
                    .replaceAll("\\s+", " ");
        }
        for (int index = 0; index < tooltips.length; index++) {
            if (config.requireViles.get() && !tooltips[index].contains("Bonus: 1 Chunk of Vile")
                    && !tooltips[index].contains("Bonus: 2 Chunk of Vile"))
                continue;
            if (matchesTask(tooltips[index])) {
                click(CHOICE_SLOTS[index], State.START, now);
                finish(true);
                return;
            }
        }
        click(CHOICE_SLOTS[ThreadLocalRandom.current().nextInt(CHOICE_SLOTS.length)], State.START,
                now);
    }

    private boolean matchesTask(String tooltip) {
        return matchesCount(tooltip, KILLS, config.kills.get())
                || matchesCount(tooltip, NO_ARMOR, config.noArmor.get())
                || matchesCount(tooltip, FIST, config.fist.get())
                || config.chain.get() >= 2 && tooltip.contains("Get a Double kill.")
                || config.chain.get() >= 3 && tooltip.contains("Get a Triple kill.")
                || config.chain.get() >= 4 && tooltip.contains("Get a Quadra kill.")
                || matchesCount(tooltip, INGOTS, config.gold.get())
                || matchesCount(tooltip, ARROWS, config.bow.get())
                || matchesCount(tooltip, NO_PERKS, config.noPerks.get())
                || matchesCount(tooltip, APPLES, config.apples.get());
    }

    private static boolean matchesCount(String tooltip, Pattern task, int maximum) {
        if (maximum == 0)
            return false;
        Matcher match = task.matcher(tooltip);
        if (!match.find())
            return false;
        try {
            return Integer.parseInt(match.group(1)) <= maximum;
        } catch (NumberFormatException invalidCount) {
            return false;
        }
    }

    private void click(int slot, State next, long now) {
        if (player.inventory.getItemStack() != null) {
            fail(language.translate("The inventory changed."));
            return;
        }
        int windowId = menu.inventorySlots.windowId;
        enter(next, now);
        input.runAction(this,
                () -> minecraft.playerController.windowClick(windowId, slot, 0, 0, player));
    }

    private void enter(State next, long now) {
        state = next;
        previousScreen = minecraft.currentScreen;
        menu = null;
        pendingStacks = null;
        clickAt = 0;
        deadline = now + (next == State.CONTRACT ? 600_000 : 10_000);
        updateHint();
    }

    private void updateHint() {
        hint = state == State.CONTRACT ? language.translate("Waiting for the contract to end…")
                : language.translate("Rerolling contracts…");
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("contractreroll", "key") && config.key.get() == Keyboard.KEY_NONE)
            finish(true);
    }

    private void failedAction(RuntimeException failure) {
        LOGGER.log(Level.WARNING, "Contract reroll failed", failure);
        fail(language.translate("An unexpected error occurred."));
    }

    private void fail(String reason) {
        finish(true);
        report(language.format("Contract reroll stopped: {0}", reason));
    }

    private void report(String message) {
        if (minecraft.thePlayer != null)
            ChatFeedback.reply(minecraft.thePlayer, Tone.ERROR, message);
    }

    private void finish(boolean resume) {
        if (state != State.IDLE)
            minecraft.gameSettings.pauseOnLostFocus = previousPauseOnLostFocus;
        state = State.IDLE;
        player = null;
        previousScreen = null;
        menu = null;
        pendingStacks = null;
        hint = null;
        MinecraftForge.EVENT_BUS.unregister(this);
        session.removeListener(sessionChanged);
        sidebar.removeListener(this);
        language.removeListener(languageChanged);
        input.unlock(this, resume);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (state == State.IDLE || event.type != RenderGameOverlayEvent.ElementType.ALL
                || minecraft.gameSettings.hideGUI || minecraft.thePlayer == null)
            return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(event.resolution.getScaledWidth() / 2.0F,
                event.resolution.getScaledHeight() / 2.0F + 35, 0);
        GlStateManager.scale(2, 2, 2);
        minecraft.fontRendererObj.drawStringWithShadow(hint,
                -FontWidths.measure(minecraft.fontRendererObj, hint) / 2.0F, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    private enum State {
        IDLE,
        LANGUAGE,
        LANGUAGE_REPLY,
        CONTRACT,
        QUESTS("Quests & Contracts", 45),
        PURCHASE("Are you sure?", 27),
        CHOICES("Pick a contract!", 27),
        START("Start event?", 27),
        CONTRACT_START;

        final String title;
        final int slots;

        State() {
            this(null, 0);
        }

        State(String title, int slots) {
            this.title = title;
            this.slots = slots;
        }
    }
}
