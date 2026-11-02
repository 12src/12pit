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
package pit12.platform.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.runtime.arrow.ArrowPacketBinding;
import pit12.runtime.arrow.ArrowPacketObserver;
import pit12.runtime.player.PlayerEquipmentPacketBinding;
import pit12.runtime.player.PlayerEquipmentPacketObserver;
import pit12.runtime.player.TabPacketBinding;
import pit12.runtime.player.TabPacketObserver;
import pit12.runtime.scoreboard.SidebarPacketBinding;
import pit12.runtime.scoreboard.SidebarPacketObserver;

@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin implements PlayerEquipmentPacketBinding,
        TabPacketBinding, SidebarPacketBinding, ArrowPacketBinding {
    @Shadow
    private WorldClient clientWorldController;
    @Unique
    private ArrowPacketObserver pit12$arrowObserver;
    @Unique
    private PlayerEquipmentPacketObserver pit12$playerEquipmentObserver;
    @Unique
    private TabPacketObserver pit12$tabObserver;
    @Unique
    private SidebarPacketObserver pit12$sidebarObserver;

    @Override
    public void bindArrowPacketObserver(ArrowPacketObserver observer) {
        pit12$arrowObserver = observer;
    }

    @Override
    public void bindPlayerEquipmentObserver(PlayerEquipmentPacketObserver observer) {
        pit12$playerEquipmentObserver = observer;
    }

    @Override
    public void bindTabObserver(TabPacketObserver observer) {
        pit12$tabObserver = observer;
    }

    @Override
    public void bindSidebarObserver(SidebarPacketObserver observer) {
        pit12$sidebarObserver = observer;
    }

    @Inject(method = "handleSpawnObject", at = @At("TAIL"))
    private void pit12$afterArrowSpawn(S0EPacketSpawnObject packet, CallbackInfo ci) {
        if (pit12$arrowObserver == null || packet.getType() != 60)
            return;
        pit12$arrowObserver.onArrowSpawn(packet.getEntityID());
    }

    @Inject(method = "handleEntityMetadata", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
            shift = At.Shift.AFTER))
    private void pit12$beforeEntityMetadata(S1CPacketEntityMetadata packet, CallbackInfo ci) {
        if (pit12$arrowObserver != null) {
            pit12$arrowObserver.beforeEntityMetadata(packet.getEntityId(), packet.func_149376_c());
        }
    }

    @Inject(method = "handleEntityEquipment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;setCurrentItemOrArmor(ILnet/minecraft/item/ItemStack;)V"))
    private void pit12$beforeHeldItemChange(S04PacketEntityEquipment packet, CallbackInfo ci) {
        if (pit12$arrowObserver != null && packet.getEquipmentSlot() == 0) {
            pit12$arrowObserver.beforeHeldItemChange(packet.getEntityID());
        }
    }

    @Inject(method = "addToSendQueue", at = @At("HEAD"))
    private void pit12$beforeBowAction(Packet<?> packet, CallbackInfo ci) {
        if (pit12$arrowObserver == null
                || !(packet instanceof C07PacketPlayerDigging
                        || packet instanceof C0BPacketEntityAction)
                || !Minecraft.getMinecraft().isCallingFromMinecraftThread())
            return;
        if (packet instanceof C07PacketPlayerDigging && ((C07PacketPlayerDigging) packet)
                .getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            pit12$arrowObserver.onLocalBowRelease();
        } else if (packet instanceof C0BPacketEntityAction && ((C0BPacketEntityAction) packet)
                .getAction() == C0BPacketEntityAction.Action.STOP_SNEAKING) {
            pit12$arrowObserver.onLocalStopSneaking();
        }
    }

    @Inject(method = "handleDestroyEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
            shift = At.Shift.AFTER))
    private void pit12$beforeEntitiesRemoved(S13PacketDestroyEntities packet, CallbackInfo ci) {
        if (pit12$arrowObserver != null) {
            for (int entityId : packet.getEntityIDs()) {
                pit12$arrowObserver.onEntityRemoved(entityId);
            }
        }
    }

    @Inject(method = "handleEntityStatus", at = @At("TAIL"))
    private void pit12$afterPlayerHurt(S19PacketEntityStatus packet, CallbackInfo ci) {
        if (pit12$arrowObserver == null || packet.getOpCode() != 2)
            return;
        Entity entity = packet.getEntity(clientWorldController);
        if (entity instanceof EntityPlayer) {
            pit12$arrowObserver.onPlayerHurt((EntityPlayer) entity);
        }
    }

    @Inject(method = "handleEntityTeleport", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
            shift = At.Shift.AFTER))
    private void pit12$beforeShooterTeleport(S18PacketEntityTeleport packet, CallbackInfo ci) {
        if (pit12$arrowObserver != null) {
            pit12$arrowObserver.onShooterTeleport(packet.getEntityId(), packet.getX() / 32.0,
                    packet.getY() / 32.0, packet.getZ() / 32.0);
        }
    }

    @Redirect(method = "handlePlayerPosLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;setPositionAndRotation(DDDFF)V"))
    private void pit12$observeLocalTeleport(EntityPlayer player, double x, double y, double z,
            float yaw, float pitch) {
        if (pit12$arrowObserver != null) {
            pit12$arrowObserver.onShooterTeleport(player.getEntityId(), x, y, z);
        }
        player.setPositionAndRotation(x, y, z, yaw, pitch);
    }

    @Redirect(method = "handleEntityVelocity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocity(DDD)V"))
    private void pit12$observeArrowVelocity(Entity entity, double x, double y, double z) {
        if (pit12$arrowObserver == null || !(entity instanceof EntityArrow)) {
            entity.setVelocity(x, y, z);
            return;
        }
        double previousX = entity.motionX;
        double previousY = entity.motionY;
        double previousZ = entity.motionZ;
        entity.setVelocity(x, y, z);
        if (entity.motionX != previousX || entity.motionY != previousY
                || entity.motionZ != previousZ) {
            pit12$arrowObserver.onArrowCorrected(entity.getEntityId(), true);
        }
    }

    @Redirect(method = {"handleEntityTeleport", "handleEntityMovement"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;setPositionAndRotation2(DDDFFIZ)V"))
    private void pit12$observeArrowPosition(Entity entity, double x, double y, double z, float yaw,
            float pitch, int increments, boolean teleport) {
        if (pit12$arrowObserver == null || !(entity instanceof EntityArrow)) {
            entity.setPositionAndRotation2(x, y, z, yaw, pitch, increments, teleport);
            return;
        }
        double previousX = entity.posX;
        double previousY = entity.posY;
        double previousZ = entity.posZ;
        entity.setPositionAndRotation2(x, y, z, yaw, pitch, increments, teleport);
        if (entity.posX != previousX || entity.posY != previousY || entity.posZ != previousZ) {
            pit12$arrowObserver.onArrowCorrected(entity.getEntityId(), false);
        }
    }

    @Inject(method = "handleBlockChange", at = @At("TAIL"))
    private void pit12$afterArrowBlockChange(S23PacketBlockChange packet, CallbackInfo ci) {
        if (pit12$arrowObserver != null) {
            pit12$arrowObserver.onBlockChanged(packet.getBlockPosition());
        }
    }

    @Inject(method = "handleMultiBlockChange", at = @At("TAIL"))
    private void pit12$afterArrowBlockChanges(S22PacketMultiBlockChange packet, CallbackInfo ci) {
        if (pit12$arrowObserver != null) {
            for (S22PacketMultiBlockChange.BlockUpdateData block : packet.getChangedBlocks()) {
                pit12$arrowObserver.onBlockChanged(block.getPos());
            }
        }
    }

    @Inject(method = "handleEntityEquipment", at = @At("TAIL"))
    private void pit12$afterEntityEquipment(S04PacketEntityEquipment packet, CallbackInfo ci) {
        PlayerEquipmentPacketObserver observer = pit12$playerEquipmentObserver;
        if (observer != null) {
            observer.onEquipmentPacket(packet.getEntityID(), packet.getEquipmentSlot());
        }
    }

    @Inject(method = "handlePlayerListItem", at = @At("TAIL"))
    private void pit12$afterPlayerListItem(S38PacketPlayerListItem packet, CallbackInfo ci) {
        TabPacketObserver observer = pit12$tabObserver;
        if (observer != null) {
            observer.onTabPacket(packet);
        }
    }

    @Inject(method = "handleScoreboardObjective", at = @At("TAIL"))
    private void pit12$afterObjective(S3BPacketScoreboardObjective packet, CallbackInfo ci) {
        // Action 2 only changes the title and score render type.
        if (pit12$sidebarObserver != null && packet.func_149338_e() != 2) {
            pit12$sidebarObserver.onSidebarPacket(packet);
        }
    }

    @Inject(method = "handleUpdateScore", at = @At("TAIL"))
    private void pit12$afterScore(S3CPacketUpdateScore packet, CallbackInfo ci) {
        if (pit12$sidebarObserver != null) {
            pit12$sidebarObserver.onSidebarPacket(packet);
        }
    }

    @Inject(method = "handleDisplayScoreboard", at = @At("TAIL"))
    private void pit12$afterDisplay(S3DPacketDisplayScoreboard packet, CallbackInfo ci) {
        if (pit12$sidebarObserver != null && packet.func_149371_c() != 0
                && packet.func_149371_c() != 2) {
            pit12$sidebarObserver.onSidebarPacket(packet);
        }
    }

    @Inject(method = "handleTeams", at = @At("TAIL"))
    private void pit12$afterTeam(S3EPacketTeams packet, CallbackInfo ci) {
        if (pit12$sidebarObserver != null) {
            pit12$sidebarObserver.onSidebarPacket(packet);
        }
    }
}
