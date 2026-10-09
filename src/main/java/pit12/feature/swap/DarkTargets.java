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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationLookup;
import pit12.feature.relation.api.RelationReadiness;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantments;
import pit12.runtime.player.PlayerEquipmentAccess;
import pit12.runtime.player.PlayerEquipmentSnapshot;

final class DarkTargets {
    private final Minecraft minecraft;
    private final SwapConfig config;
    private final PlayerEquipmentAccess equipment;
    private final RelationLookup relations;
    // Keep triggered players until they recover to avoid swapping near the equip threshold.
    private final Map<UUID, EntityPlayer> targets = new HashMap<>();
    private UUID lastAttackedId;
    private int lastAttackedEntityId;

    DarkTargets(Minecraft minecraft, SwapConfig config, PlayerEquipmentAccess equipment,
            RelationLookup relations) {
        this.minecraft = minecraft;
        this.config = config;
        this.equipment = equipment;
        this.relations = relations;
    }

    void attack(EntityPlayer target) {
        if (!config.enabled() || !config.autoSwap.get() || !config.dark.get() || !ready()
                || minecraft.theWorld == null || target.worldObj != minecraft.theWorld
                || minecraft.theWorld.getEntityByID(target.getEntityId()) != target
                || target == minecraft.thePlayer || target.isDead || target.isSpectator()
                || target.getHealth() <= 0 || !allowed(target))
            return;
        lastAttackedId = target.getUniqueID();
        lastAttackedEntityId = target.getEntityId();
    }

    boolean ready() {
        return !config.darkEnemiesOnly.get() && !config.darkIgnoreFriends.get()
                || relations.readiness() == RelationReadiness.READY;
    }

    boolean needed() {
        if (!config.dark.get() || minecraft.theWorld == null || minecraft.thePlayer == null) {
            reset();
            return false;
        }
        EntityPlayer last = null;
        if (lastAttackedId != null) {
            Entity entity = minecraft.theWorld.getEntityByID(lastAttackedEntityId);
            if (entity instanceof EntityPlayer && lastAttackedId.equals(entity.getUniqueID())
                    && !entity.isDead && ((EntityPlayer) entity).getHealth() > 0)
                last = (EntityPlayer) entity;
            else
                lastAttackedId = null;
        }
        if (!ready())
            return false;
        boolean nearby = config.darkTriggerMode.get() == 1;
        Iterator<EntityPlayer> iterator = targets.values().iterator();
        while (iterator.hasNext()) {
            EntityPlayer target = iterator.next();
            if (!nearby && target != last || !matches(target, true))
                iterator.remove();
        }
        if (nearby) {
            for (EntityPlayer target : minecraft.theWorld.playerEntities) {
                if (!targets.containsKey(target.getUniqueID()) && matches(target, false))
                    targets.put(target.getUniqueID(), target);
            }
        } else if (last != null && !targets.containsKey(lastAttackedId) && matches(last, false)) {
            targets.put(lastAttackedId, last);
        }
        return !targets.isEmpty();
    }

    private boolean allowed(EntityPlayer target) {
        Relation relation = relations.relationOf(target.getUniqueID());
        return (!config.darkEnemiesOnly.get() || relation == Relation.ENEMY)
                && (!config.darkIgnoreFriends.get() || relation != Relation.FRIEND);
    }

    private boolean matches(EntityPlayer target, boolean retained) {
        if (target == minecraft.thePlayer || target.isDead || target.isSpectator()
                || minecraft.theWorld.getEntityByID(target.getEntityId()) != target
                || !allowed(target))
            return false;
        float health = target.getHealth();
        int range = config.darkRange.get();
        if (!(health > 0) || target.getDistanceSqToEntity(minecraft.thePlayer) > range * range
                || health > config.darkThreshold.get()
                        && (!retained || health >= config.darkRestoreThreshold.get()))
            return false;
        PlayerEquipmentSnapshot snapshot = equipment.loadedEquipment(target.getUniqueID());
        if (snapshot == null || !snapshot.leggingsKnown())
            return false;
        PitEnchantments enchantments = snapshot.leggingsEnchantments();
        return enchantments.contains(PitEnchantment.Phoenix)
                || enchantments.contains(PitEnchantment.Escape_Pod)
                        && (!config.skipLowPod.get() || health >= 4);
    }

    void reset() {
        lastAttackedId = null;
        targets.clear();
    }
}
