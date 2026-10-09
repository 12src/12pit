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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import pit12.shared.lifecycle.ClientLifecycle;

public final class AutoQuickMathFeature implements ClientLifecycle {
    private static final Logger LOGGER = Logger.getLogger(AutoQuickMathFeature.class.getName());
    private static final Pattern QUICK_MATHS_PATTERN = Pattern.compile(
            "^QUICK MATHS!\\s+Solve:\\s*([0-9+\\-*x×÷/()\\s]+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final int TICKS_PER_SECOND = 20;
    private final AutoQuickMathConfig config;
    private boolean started;
    private String pendingAnswer;
    private int delayTicks;

    public AutoQuickMathFeature(AutoQuickMathConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        clearPendingAnswer();
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!config.enabled()) {
            clearPendingAnswer();
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

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) {
            return;
        }
        if (!config.enabled()) {
            clearPendingAnswer();
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
