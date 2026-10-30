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
package pit12.feature.misc.chatmacro;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import pit12.runtime.command.CommandRegistry;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.input.InputBlocker;
import pit12.runtime.input.InputHandler;
import pit12.runtime.languages.Languages;
import pit12.runtime.scoreboard.SidebarCache;
import pit12.runtime.scoreboard.SidebarLine;
import pit12.runtime.scoreboard.SidebarListener;
import pit12.runtime.session.ClientSession;
import pit12.shared.chat.ChatFeedback;
import pit12.shared.chat.ChatFeedback.Tone;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.input.Keybind;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.rendering.FontWidths;

public final class ChatMacroFeature
        implements ClientLifecycle, InputHandler, SidebarListener, ConfigChangeListener {
    private final Minecraft minecraft;
    private final ConfigCatalog configs;
    private final ChatMacroConfig config;
    private final CommandRegistry commands;
    private final ClientSession session;
    private final SidebarCache sidebar;
    private final InputBlocker input;
    private final Languages language;
    private final MacroBindings bindings;
    private final Runnable sessionListener = () -> cancel(false);
    private final Runnable languageListener = this::updateHint;
    private boolean started;
    private boolean waiting;
    private EntityPlayerSP waitingPlayer;
    private long waitingRevision;
    private String spawnHint;
    private int hintWidth = -1;
    private FontRenderer hintFont;
    private boolean hintUnicode;
    private String pending;
    private EntityPlayerSP pendingPlayer;
    private boolean pendingSpawn;
    private boolean armed;

    public ChatMacroFeature(Minecraft minecraft, ClientThread client, ConfigCatalog configs,
            ChatMacroConfig config, ClientSession session, SidebarCache sidebar, InputBlocker input,
            CommandRegistry commands, Languages language, Path path) {
        this.minecraft = minecraft;
        this.configs = configs;
        this.config = config;
        this.commands = commands;
        this.session = session;
        this.sidebar = sidebar;
        this.input = input;
        this.language = language;
        bindings = new MacroBindings(client, path, message -> {
            if (minecraft.thePlayer != null)
                ChatFeedback.reply(minecraft.thePlayer, Tone.ERROR, language.translate(message));
        });
        commands.register(new ChatMacroCommand(bindings, language).definition(), config, true);
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        bindings.start();
        input.addHandler(this);
        configs.addListener(this);
        session.addListener(sessionListener);
        language.addListener(languageListener);
        MinecraftForge.EVENT_BUS.register(this);
        commands.setActive(config, true);
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        commands.setActive(config, false);
        cancel(false);
        input.removeHandler(this);
        configs.removeListener(this);
        session.removeListener(sessionListener);
        language.removeListener(languageListener);
        MinecraftForge.EVENT_BUS.unregister(this);
        bindings.stop();
    }

    @Override
    public boolean consumesKey(int key) {
        return key > 0
                && (Keybind.matches(config.spawnKey.get(), key) || bindings.message(key) != null);
    }

    @Override
    public boolean key(int key, boolean pressed, boolean repeat) {
        if (!pressed || !consumesKey(key) || minecraft.currentScreen != null
                || !minecraft.inGameHasFocus || minecraft.thePlayer == null
                || minecraft.theWorld == null)
            return false;
        if (repeat || pending != null)
            return true;
        if (Keybind.matches(config.spawnKey.get(), key)) {
            if (waiting)
                cancel(true);
            else if (canSpawn())
                send("/spawn", true);
            else
                waitForSpawn();
        } else {
            send(bindings.message(key), false);
        }
        return true;
    }

    private void send(String message, boolean spawn) {
        boolean block = config.blockInputs.get();
        if (!block && !input.locked()) {
            minecraft.thePlayer.sendChatMessage(message);
            if (spawn)
                stopWaiting();
            return;
        }
        pending = message;
        pendingPlayer = minecraft.thePlayer;
        pendingSpawn = spawn;
        armed = !block;
        if (block)
            input.lock(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (waiting && waitingPlayer != minecraft.thePlayer
                || pending != null && pendingPlayer != minecraft.thePlayer) {
            cancel(false);
            return;
        }
        if (pending == null || input.blockedFor(this))
            return;
        if (!armed) {
            // Give the player update one more tick to send stopped movement first.
            armed = true;
            return;
        }
        if (pendingSpawn && !canSpawn()) {
            pending = null;
            pendingPlayer = null;
            pendingSpawn = false;
            input.unlock(this, true);
            if (!waiting)
                waitForSpawn();
            return;
        }
        try {
            minecraft.thePlayer.sendChatMessage(pending);
        } finally {
            if (pendingSpawn)
                stopWaiting();
            pending = null;
            pendingPlayer = null;
            pendingSpawn = false;
            armed = false;
            input.unlock(this, true);
        }
        if (waiting && canSpawn())
            send("/spawn", true);
    }

    @Override
    public void onLineChanged(SidebarLine previous, SidebarLine current) {
        if (waiting && waitingRevision != session.revision()) {
            cancel(false);
            return;
        }
        if (waiting && pending == null && canSpawn())
            send("/spawn", true);
    }

    private void waitForSpawn() {
        waiting = true;
        waitingPlayer = minecraft.thePlayer;
        waitingRevision = session.revision();
        sidebar.addListener(this);
        updateHint();
    }

    private boolean canSpawn() {
        for (SidebarLine line : sidebar.lines()) {
            String text = line.formattedText(minecraft.fontRendererObj);
            if (text.contains("Fighting") || text.contains("战斗中"))
                return false;
        }
        return true;
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("chatmacro", "spawn_key")) {
            if (config.spawnKey.get() == Keyboard.KEY_NONE) {
                if (pendingSpawn)
                    cancel(true);
                else
                    stopWaiting();
            } else {
                updateHint();
            }
        }
    }

    private void updateHint() {
        if (waiting) {
            spawnHint = language.format("Spawning… Press [{0}] to cancel.",
                    Keybind.name(config.spawnKey.get()));
            hintWidth = -1;
        }
    }

    private void stopWaiting() {
        if (waiting)
            sidebar.removeListener(this);
        waiting = false;
        waitingPlayer = null;
    }

    private void cancel(boolean resume) {
        stopWaiting();
        pending = null;
        pendingPlayer = null;
        pendingSpawn = false;
        armed = false;
        input.unlock(this, resume);
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Post event) {
        hintWidth = -1;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!waiting || event.type != ElementType.ALL || minecraft.gameSettings.hideGUI
                || minecraft.thePlayer == null)
            return;
        FontRenderer font = minecraft.fontRendererObj;
        boolean unicode = font.getUnicodeFlag();
        if (hintWidth < 0 || hintFont != font || hintUnicode != unicode) {
            hintWidth = FontWidths.measure(font, spawnHint);
            hintFont = font;
            hintUnicode = unicode;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(event.resolution.getScaledWidth() / 2.0F,
                    event.resolution.getScaledHeight() / 2.0F + 35, 0);
            GlStateManager.scale(2, 2, 2);
            font.drawStringWithShadow(spawnHint, -hintWidth / 2.0F, 0, 0xFFFFFF);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
