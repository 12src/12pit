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
package pit12.feature.quickmath;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Chat;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoQuickMathFeature implements ClientLifecycle, ConfigChangeListener {
    private final Consumer<Chat> onChatReceivedListener = this::onChatReceived;
    private final Runnable onClientTickListener = this::onClientTick;

    private static final Logger LOGGER = Logger.getLogger(AutoQuickMathFeature.class.getName());
    private static final Pattern QUICK_MATHS_PATTERN = Pattern.compile(
            "^QUICK MATHS!\\s+Solve:\\s*([0-9+\\-*x×÷/()\\s]+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final int TICKS_PER_SECOND = 20;
    private final ConfigCatalog configs;
    private final AutoQuickMathConfig config;
    private boolean started;
    private boolean active;
    private String pendingAnswer;
    private int delayTicks;

    public AutoQuickMathFeature(ConfigCatalog configs, AutoQuickMathConfig config) {
        this.configs = configs;
        this.config = config;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        configs.addListener(this);
        updateActivation();
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        configs.removeListener(this);
        updateActivation();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (changes.affects("auto_quick_math", "enabled")) {
            updateActivation();
        }
    }

    private void updateActivation() {
        boolean enabled = started && config.enabled();
        if (active == enabled) {
            return;
        }
        active = enabled;
        if (active) {
            ClientEvents.CHAT.add(onChatReceivedListener);
            ClientEvents.TICK_START.add(onClientTickListener);
        } else {
            ClientEvents.CHAT.remove(onChatReceivedListener);
            ClientEvents.TICK_START.remove(onClientTickListener);
            clearPendingAnswer();
        }
    }

    private void onChatReceived(Chat event) {
        if (!active) {
            return;
        }
        Matcher matcher = QUICK_MATHS_PATTERN.matcher(event.message.getUnformattedText());
        if (!matcher.matches()) {
            return;
        }
        String expression = matcher.group(1);
        try {
            long answer = QuickMathExpression.evaluate(expression);
            pendingAnswer = String.valueOf(answer);
            delayTicks = (int) Math.round(config.delay() * TICKS_PER_SECOND);
            LOGGER.info("Solved Quick Math expression " + expression + " = " + answer
                    + ". Answering in " + delayTicks + " ticks.");
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to solve Quick Math expression: '" + expression + "'",
                    failure);
        }
    }

    private void onClientTick() {
        if (!active) {
            return;
        }
        if (pendingAnswer == null) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            clearPendingAnswer();
            return;
        }
        if (delayTicks > 0 && --delayTicks > 0) {
            return;
        }
        String answer = pendingAnswer;
        clearPendingAnswer();
        try {
            player.sendChatMessage("/ac " + answer);
            LOGGER.info("Sent Quick Math answer: " + answer);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to send Quick Math answer: " + answer, failure);
        }
    }

    private void clearPendingAnswer() {
        pendingAnswer = null;
        delayTicks = 0;
    }
}
