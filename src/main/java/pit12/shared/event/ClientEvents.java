package pit12.shared.event;


import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class ClientEvents {
    public static final EventChannel<Runnable> INPUT_TICK = new EventChannel<>();
    public static final EventChannel<Runnable> TICK_START = new EventChannel<>();
    public static final EventChannel<Runnable> TICK_END = new EventChannel<>();
    public static final EventChannel<Runnable> KEY_INPUT = new EventChannel<>();
    public static final EventChannel<Runnable> TEXTURES_RELOADED = new EventChannel<>();
    public static final EventChannel<Consumer<Overlay>> OVERLAY = new EventChannel<>();
    public static final EventChannel<WorldRenderer> WORLD_RENDER = new EventChannel<>();
    public static final EventChannel<WorldRenderer> WORLD_RENDER_LAST = new EventChannel<>();
    public static final EventChannel<Consumer<Chat>> CHAT = new EventChannel<>();
    public static final EventChannel<Consumer<EntityJoined>> ENTITY_JOIN_LAST = new EventChannel<>();
    public static final EventChannel<Consumer<Attack>> ATTACK = new EventChannel<>();
    public static final EventChannel<Consumer<Attack>> ATTACK_LAST = new EventChannel<>();
    public static final EventChannel<Consumer<Interaction>> INTERACT = new EventChannel<>();
    public static final EventChannel<Consumer<Sound>> SOUND = new EventChannel<>();
    public static final EventChannel<Consumer<NameRender>> NAME_RENDER = new EventChannel<>();
    public static final EventChannel<Consumer<GuiScreen>> GUI_OPEN = new EventChannel<>();
    public static final EventChannel<Consumer<ScreenDraw>> GUI_DRAW = new EventChannel<>();
    public static final EventChannel<Consumer<Chunk>> CHUNK_LOAD = new EventChannel<>();
    public static final EventChannel<Consumer<World>> WORLD_UNLOAD = new EventChannel<>();
    public static final EventChannel<Consumer<NetworkManager>> DISCONNECT = new EventChannel<>();

    private ClientEvents() {}

    @FunctionalInterface
    public interface WorldRenderer {
        void render(float partialTicks);
    }

    public static final class Overlay {
        public final ScaledResolution resolution;
        public final float partialTicks;

        public Overlay(ScaledResolution resolution, float partialTicks) {
            this.resolution = resolution;
            this.partialTicks = partialTicks;
        }
    }

    public static final class Chat {
        public final byte type;
        public final IChatComponent message;

        public Chat(byte type, IChatComponent message) {
            this.type = type;
            this.message = message;
        }
    }

    public static final class EntityJoined {
        public final Entity entity;
        public final World world;

        public EntityJoined(Entity entity, World world) {
            this.entity = entity;
            this.world = world;
        }
    }

    public static final class Attack {
        public final EntityPlayer entityPlayer;
        public final Entity target;

        public Attack(EntityPlayer player, Entity target) {
            this.entityPlayer = player;
            this.target = target;
        }
    }

    public static final class Interaction {
        public enum Action {
            LEFT_CLICK_BLOCK, RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR
        }

        public final EntityPlayer entityPlayer;
        public final Action action;
        // Auto Fish reads this after all platform listeners have handled the click.
        public final BooleanSupplier canceled;

        public Interaction(EntityPlayer player, Action action, BooleanSupplier canceled) {
            this.entityPlayer = player;
            this.action = action;
            this.canceled = canceled;
        }
    }

    public static final class Sound {
        public final Entity entity;
        public final String name;

        public Sound(Entity entity, String name) {
            this.entity = entity;
            this.name = name;
        }
    }

    public static final class NameRender {
        public final EntityLivingBase entity;
        public final RendererLivingEntity<?> renderer;
        public final double x;
        public final double y;
        public final double z;
        public final float nameRange;

        public NameRender(EntityLivingBase entity, RendererLivingEntity<?> renderer, double x,
                double y, double z, float nameRange) {
            this.entity = entity;
            this.renderer = renderer;
            this.x = x;
            this.y = y;
            this.z = z;
            this.nameRange = nameRange;
        }
    }

    public static final class ScreenDraw {
        public final GuiScreen gui;
        public boolean canceled;

        public ScreenDraw(GuiScreen gui) {
            this.gui = gui;
        }
    }
}
