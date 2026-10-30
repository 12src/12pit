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
package pit12.feature.render.timers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import pit12.runtime.arrow.ArrowHit;
import pit12.runtime.arrow.ArrowListener;
import pit12.runtime.arrow.ArrowTracker;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;

final class PinTracker implements ArrowListener {
    private final TimersConfig config;
    private final ArrowTracker arrows;
    private final BiConsumer<EntityPlayer, Long> triggered;
    private final Runnable resetTimers;
    private final Map<Integer, Shot> shots = new HashMap<>();

    PinTracker(TimersConfig config, ArrowTracker arrows, BiConsumer<EntityPlayer, Long> triggered,
            Runnable resetTimers) {
        this.config = config;
        this.arrows = arrows;
        this.triggered = triggered;
        this.resetTimers = resetTimers;
    }

    void active(boolean active) {
        if (active) {
            arrows.addListener(this);
        } else {
            arrows.removeListener(this);
            onArrowsReset();
        }
    }

    @Override
    public boolean onArrowSpawn(EntityArrow arrow, EntityPlayer shooter, ItemStack bow,
            boolean sneaking, long lastSneakingNanos) {
        int level = PitEnchantmentReader.levelOf(bow, PitEnchantment.Pin_down);
        int seconds = level == 1 ? 3 : level == 2 ? 5 : level == 3 ? 10 : 0;
        if (seconds == 0)
            return false;
        shots.put(arrow.getEntityId(),
                new Shot(seconds, PitEnchantmentReader.contains(bow, PitEnchantment.Mega_Longbow)));
        return true;
    }

    @Override
    public long hitWindowNanos() {
        return config.pinHitWindow.get() * 1_000_000L;
    }

    @Override
    public void onArrowHit(EntityPlayer target, List<ArrowHit> candidates, long hitAt) {
        int seconds = -1;
        for (ArrowHit candidate : candidates) {
            Shot shot = shots.get(candidate.arrow.getEntityId());
            int effect =
                    shot != null && (shot.megaLongbow || candidate.critical) ? shot.seconds : 0;
            if (seconds >= 0 && seconds != effect)
                return;
            seconds = effect;
        }
        if (seconds > 0)
            triggered.accept(target, hitAt + seconds * 1_000_000_000L);
    }

    @Override
    public void onArrowFinished(int entityId) {
        shots.remove(entityId);
    }

    @Override
    public void onArrowsReset() {
        shots.clear();
        resetTimers.run();
    }

    private static final class Shot {
        final int seconds;
        final boolean megaLongbow;

        Shot(int seconds, boolean megaLongbow) {
            this.seconds = seconds;
            this.megaLongbow = megaLongbow;
        }
    }
}
