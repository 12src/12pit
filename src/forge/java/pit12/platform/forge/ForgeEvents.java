package pit12.platform.forge;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import pit12.platform.oneconfig.OneConfigSupport;
import pit12.runtime.config.ConfigCatalog;
import pit12.shared.event.ClientEvents;
import pit12.shared.event.ClientEvents.Attack;
import pit12.shared.event.ClientEvents.Chat;
import pit12.shared.event.ClientEvents.EntityJoined;
import pit12.shared.event.ClientEvents.Interaction;
import pit12.shared.event.ClientEvents.NameRender;
import pit12.shared.event.ClientEvents.Overlay;
import pit12.shared.event.ClientEvents.ScreenDraw;
import pit12.shared.event.ClientEvents.Sound;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ForgeEvents implements ClientLifecycle {
    private final OneConfigSupport oneConfig;

    public ForgeEvents(ConfigCatalog configs) {
        oneConfig = new OneConfigSupport(configs);
    }

    @Override
    public void start() {
        MinecraftForge.EVENT_BUS.register(this);
        oneConfig.start();
    }

    @Override
    public void stop() {
        try {
            oneConfig.stop();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInputTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
            ClientEvents.INPUT_TICK.publish(Runnable::run);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
            ClientEvents.TICK_START.publish(Runnable::run);
        else
            ClientEvents.TICK_END.publish(Runnable::run);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        ClientEvents.KEY_INPUT.publish(Runnable::run);
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Post event) {
        ClientEvents.TEXTURES_RELOADED.publish(Runnable::run);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !ClientEvents.OVERLAY.listening())
            return;
        Overlay overlay = new Overlay(event.resolution, event.partialTicks);
        ClientEvents.OVERLAY.publish(listener -> listener.accept(overlay));
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        ClientEvents.WORLD_RENDER.publish(listener -> listener.render(event.partialTicks));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        ClientEvents.WORLD_RENDER_LAST.publish(listener -> listener.render(event.partialTicks));
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!ClientEvents.CHAT.listening())
            return;
        Chat chat = new Chat(event.type, event.message);
        ClientEvents.CHAT.publish(listener -> listener.accept(chat));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinLast(EntityJoinWorldEvent event) {
        if (!ClientEvents.ENTITY_JOIN_LAST.listening())
            return;
        EntityJoined joined = new EntityJoined(event.entity, event.world);
        ClientEvents.ENTITY_JOIN_LAST.publish(listener -> listener.accept(joined));
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!ClientEvents.ATTACK.listening())
            return;
        Attack attack = new Attack(event.entityPlayer, event.target);
        ClientEvents.ATTACK.publish(listener -> listener.accept(attack));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttackLast(AttackEntityEvent event) {
        if (!ClientEvents.ATTACK_LAST.listening())
            return;
        Attack attack = new Attack(event.entityPlayer, event.target);
        ClientEvents.ATTACK_LAST.publish(listener -> listener.accept(attack));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!ClientEvents.INTERACT.listening())
            return;
        Interaction interaction = new Interaction(event.entityPlayer,
                Interaction.Action.valueOf(event.action.name()), event::isCanceled);
        ClientEvents.INTERACT.publish(listener -> listener.accept(interaction));
    }

    @SubscribeEvent
    public void onSound(PlaySoundAtEntityEvent event) {
        if (!ClientEvents.SOUND.listening())
            return;
        Sound sound = new Sound(event.entity, event.name);
        ClientEvents.SOUND.publish(listener -> listener.accept(sound));
    }

    @SubscribeEvent
    public void onRenderName(RenderLivingEvent.Post<?> event) {
        if (!ClientEvents.NAME_RENDER.listening())
            return;
        float range = event.entity.isSneaking() ? RendererLivingEntity.NAME_TAG_RANGE_SNEAK
                : RendererLivingEntity.NAME_TAG_RANGE;
        NameRender render = new NameRender(event.entity, event.renderer, event.x, event.y, event.z, range);
        ClientEvents.NAME_RENDER.publish(listener -> listener.accept(render));
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientEvents.GUI_OPEN.publish(listener -> listener.accept(event.gui));
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!ClientEvents.GUI_DRAW.listening())
            return;
        ScreenDraw draw = new ScreenDraw(event.gui);
        ClientEvents.GUI_DRAW.publish(listener -> listener.accept(draw));
        if (draw.canceled)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        ClientEvents.CHUNK_LOAD.publish(listener -> listener.accept(event.getChunk()));
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        ClientEvents.WORLD_UNLOAD.publish(listener -> listener.accept(event.world));
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientEvents.DISCONNECT.publish(listener -> listener.accept(event.manager));
    }
}
