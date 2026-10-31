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
package pit12.feature.render.imagepopup;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.Setting;
import pit12.runtime.input.InputTicks;
import pit12.runtime.session.ClientSession;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.lifecycle.ClientLifecycle;
import pit12.shared.rendering.UiRenderer;

/**
 * Shows a random image from config/12pit/splash for a few seconds, every few minutes. Timing and layout are configured
 * in the settings UI.
 */
public final class ImagePopupFeature
        implements ClientLifecycle, ConfigChangeListener, InputTicks.Listener {
    private static final Logger LOGGER = LogManager.getLogger(ImagePopupFeature.class);
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int MAX_TEXTURE_SIZE = 4096;
    private final Minecraft minecraft;
    private final ClientThread client;
    private final ConfigCatalog configs;
    private final ImagePopupConfig config;
    private final ClientSession session;
    private final InputTicks inputTicks;
    private final Path directory;
    private final UiRenderer renderer;
    private final Random random = new Random();
    private final Runnable sessionListener = this::onSessionChanged;
    private volatile String lastFile;
    private ExecutorService worker;
    private long nextShowAt;
    private long showStart;
    private long hideAt;
    private ResourceLocation texture;
    private int textureWidth;
    private int textureHeight;
    private int requestCounter;
    private boolean warnedEmpty;
    private boolean started;

    public ImagePopupFeature(Minecraft minecraft, ClientThread client, ConfigCatalog configs,
            ImagePopupConfig config, ClientSession session, InputTicks inputTicks, Path directory) {
        this.minecraft = minecraft;
        this.client = client;
        this.configs = configs;
        this.config = config;
        this.session = session;
        this.inputTicks = inputTicks;
        this.directory = directory;
        this.renderer = new UiRenderer(minecraft);
    }

    @Override
    public void start() {
        session.checkThread();
        if (started) {
            return;
        }
        started = true;
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "12pit-image-popup");
            thread.setDaemon(true);
            return thread;
        });
        configs.addListener(this);
        inputTicks.addListener(this);
        session.addListener(sessionListener);
        MinecraftForge.EVENT_BUS.register(this);
        resetTimer();
        worker.execute(this::prepareDirectory);
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        requestCounter++;
        MinecraftForge.EVENT_BUS.unregister(this);
        configs.removeListener(this);
        inputTicks.removeListener(this);
        session.removeListener(sessionListener);
        stopWorker();
        releaseTexture();
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        for (Setting<?> setting : config.settings()) {
            if (!changes.affects(config.id(), setting.id())) {
                continue;
            }
            if (!config.enabled()) {
                hide();
            } else if (changes.affects(config.id(), config.intervalMinutes().id())
                    || changes.affects(config.id(), config.intervalSeconds().id())) {
                resetTimer();
            }
            return;
        }
    }

    @Override
    public void onInputTick(InputTicks.Phase phase) {
        if (phase != InputTicks.Phase.START) {
            return;
        }
        tick();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL || !started || texture == null || !config.enabled()
                || minecraft.gameSettings.hideGUI || minecraft.thePlayer == null) {
            return;
        }
        long now = System.nanoTime();
        if (now >= hideAt) {
            return;
        }
        int alpha = alpha(now);
        if (alpha <= 0) {
            return;
        }
        int screenWidth = event.resolution.getScaledWidth();
        int screenHeight = event.resolution.getScaledHeight();
        renderer.resize(event.resolution.getScaleFactor());
        double maxWidth = screenWidth * config.maxWidthPercent().get() / 100.0D;
        double maxHeight = screenHeight * config.maxHeightPercent().get() / 100.0D;
        double factor = Math.min(maxWidth / textureWidth, maxHeight / textureHeight);
        int width = Math.max(1, (int) Math.round(textureWidth * factor));
        int height = Math.max(1, (int) Math.round(textureHeight * factor));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        if (config.dimBackground().get()) {
            renderer.rect(0, 0, screenWidth, screenHeight, (alpha * 0x66 / 255) << 24);
        }
        drawTexture(texture, x, y, width, height, (alpha << 24) | 0xFFFFFF);
    }

    private void drawTexture(ResourceLocation location, int x, int y, int width, int height,
            int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        minecraft.getTextureManager().bindTexture(location);
        GlStateManager.color((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F);
        try {
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, width, height, width, height);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void tick() {
        if (!started || !config.enabled() || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextShowAt) {
            return;
        }
        nextShowAt = now + intervalNanos();
        requestImage();
    }

    private void requestImage() {
        ExecutorService executor = worker;
        if (executor == null) {
            return;
        }
        final int request = ++requestCounter;
        executor.execute(() -> {
            List<Path> images = listImages();
            if (images.isEmpty()) {
                if (!warnedEmpty) {
                    warnedEmpty = true;
                    LOGGER.info("No images found in {}", directory.toAbsolutePath());
                }
                return;
            }
            warnedEmpty = false;
            Path file = pick(images);
            BufferedImage image = read(file);
            if (image == null) {
                return;
            }
            try {
                client.execute(() -> show(request, file, image));
            } catch (RuntimeException ignored) {
                // The client is shutting down.
            }
        });
    }

    private void show(int request, Path file, BufferedImage image) {
        if (!started || request != requestCounter || !config.enabled()
                || minecraft.theWorld == null) {
            return;
        }
        releaseTexture();
        String name = file.getFileName().toString();
        ResourceLocation location = new ResourceLocation("pit12",
                "image_popup/" + Integer.toHexString(name.hashCode()));
        minecraft.getTextureManager().loadTexture(location, new DynamicTexture(image));
        texture = location;
        textureWidth = image.getWidth();
        textureHeight = image.getHeight();
        lastFile = name;
        showStart = System.nanoTime();
        hideAt = showStart + Math.max(1, config.durationSeconds().get()) * NANOS_PER_SECOND;
        if (config.playSound().get() && minecraft.thePlayer != null) {
            minecraft.thePlayer.playSound("random.pop", 0.4F, 1.0F);
        }
    }

    private int alpha(long now) {
        int fadeMillis = config.fadeMillis().get();
        if (fadeMillis <= 0) {
            return 255;
        }
        long fade = fadeMillis * 1_000_000L;
        float fadeIn = (now - showStart) / (float) fade;
        float fadeOut = (hideAt - now) / (float) fade;
        float value = Math.min(1.0F, Math.min(fadeIn, fadeOut));
        return value <= 0.0F ? 0 : Math.round(value * 255.0F);
    }

    private void hide() {
        hideAt = 0;
        releaseTexture();
    }

    private void releaseTexture() {
        if (texture != null) {
            minecraft.getTextureManager().deleteTexture(texture);
            texture = null;
        }
        textureWidth = 0;
        textureHeight = 0;
    }

    private void prepareDirectory() {
        try {
            Files.createDirectories(directory);
            LOGGER.info("Image popup folder: {}", directory.toAbsolutePath());
        } catch (IOException failure) {
            LOGGER.warn("Could not create {}", directory.toAbsolutePath(), failure);
        }
    }

    private List<Path> listImages() {
        List<Path> images = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return images;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).filter(ImagePopupFeature::isImage)
                    .forEach(images::add);
        } catch (IOException failure) {
            LOGGER.warn("Could not list {}", directory.toAbsolutePath(), failure);
        }
        return images;
    }

    private static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private Path pick(List<Path> images) {
        List<Path> candidates = images;
        if (images.size() > 1 && lastFile != null) {
            List<Path> withoutLast = new ArrayList<>();
            for (Path image : images) {
                if (!image.getFileName().toString().equals(lastFile)) {
                    withoutLast.add(image);
                }
            }
            if (!withoutLast.isEmpty()) {
                candidates = withoutLast;
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private BufferedImage read(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                LOGGER.warn("Not an image: {}", file);
                return null;
            }
            return scaleDown(image);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not read {}", file, failure);
            return null;
        }
    }

    private static BufferedImage scaleDown(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double factor = Math.min(1.0, (double) MAX_TEXTURE_SIZE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));
        if (targetWidth == width && targetHeight == height
                && image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage copy =
                new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private void onSessionChanged() {
        hide();
        resetTimer();
    }

    private void resetTimer() {
        nextShowAt = System.nanoTime() + intervalNanos();
    }

    private long intervalNanos() {
        long seconds = config.intervalMinutes().get() * 60L + config.intervalSeconds().get();
        return Math.max(1L, seconds) * NANOS_PER_SECOND;
    }

    private void stopWorker() {
        ExecutorService executor = worker;
        worker = null;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(1L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
