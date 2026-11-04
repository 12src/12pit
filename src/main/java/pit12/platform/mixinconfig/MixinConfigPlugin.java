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
package pit12.platform.mixinconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.util.Annotations;
import pit12.platform.lunar.LunarSupport;

public final class MixinConfigPlugin implements IMixinConfigPlugin, IMixinErrorHandler {
    private static final Logger LOGGER = LogManager.getLogger(MixinConfigPlugin.class);
    // Mixin creates a separate instance for error callbacks.
    private static final Set<String> FAILED_MIXINS = new ConcurrentSkipListSet<>();
    private static final AtomicInteger FAILURE_COUNT = new AtomicInteger();
    private final BiFunction<String, MethodNode, String> handlerSignature;
    private boolean lunar;
    private boolean optifine;
    private boolean patcher;

    public MixinConfigPlugin() {
        this((mixinClassName, method) -> {
            // Use Mixin's name mapping instead of depending on its handler naming format.
            ClassInfo.Method handler =
                    ClassInfo.forName(mixinClassName).findMethod(method, ClassInfo.INCLUDE_ALL);
            return handler.getName() + handler.getDesc();
        });
    }

    MixinConfigPlugin(BiFunction<String, MethodNode, String> handlerSignature) {
        this.handlerSignature = handlerSignature;
    }

    public static List<String> failedMixinNames() {
        return new ArrayList<>(FAILED_MIXINS);
    }

    public static int failedMixinCount() {
        return FAILURE_COUNT.get();
    }

    private static void recordFailure(String mixinName) {
        if (FAILED_MIXINS.add(mixinName)) {
            FAILURE_COUNT.incrementAndGet();
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
        Mixins.registerErrorHandlerClass(MixinConfigPlugin.class.getName());
        lunar = LunarSupport.isLoaded();
        // Forge detects OptiFine after Mixin preparation.
        optifine =
                Launch.classLoader.getResource("optifine/OptiFineClassTransformer.class") != null;
        patcher = !lunar && Launch.classLoader
                .getResource("club/sk1er/patcher/config/PatcherConfig.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("pit12.platform.mixin.rendering.VanillaFontRendererMixin")) {
            return !optifine;
        }
        if (mixinClassName.equals("pit12.platform.mixin.rendering.OptiFineFontRendererMixin")) {
            return optifine && !lunar && !patcher;
        }
        if (mixinClassName.equals("pit12.platform.mixin.rendering.LunarFontRendererMixin")) {
            return optifine && lunar;
        }
        if (mixinClassName.equals("pit12.platform.mixin.rendering.PatcherFontRendererMixin")
                || mixinClassName.equals("pit12.platform.mixin.accessor.PatcherConfigAccessor")) {
            return optifine && patcher;
        }
        if (mixinClassName
                .equals("pit12.feature.render.nametag.mixin.PatcherRendererLivingEntityMixin")) {
            return patcher;
        }
        if (mixinClassName.startsWith("pit12.feature.render.focus.mixin.OptiFine")) {
            return optifine
                    && Launch.classLoader.getResource("net/optifine/shaders/Shaders.class") != null;
        }
        if (mixinClassName.equals("pit12.feature.render.focus.mixin.LunarRenderItemMixin")) {
            return lunar;
        }
        if (mixinClassName.startsWith("pit12.platform.mixin.oneconfig.")) {
            return Launch.classLoader
                    .getResource("cc/polyfrost/oneconfig/config/Config.class") != null;
        }
        if (mixinClassName
                .equals("pit12.feature.render.nametag.mixin.VanillaRendererLivingEntityMixin")) {
            return !lunar;
        }
        if (mixinClassName
                .equals("pit12.feature.render.nametag.mixin.LunarEntityLivingBaseMixin")) {
            return lunar;
        }
        if (mixinClassName.startsWith("pit12.feature.patch.fontcachefix.mixin.")) {
            if (lunar) {
                return !mixinClassName.endsWith(".FontRendererHookMixin") && optifine;
            }
            return !mixinClassName.endsWith(".LunarFontRendererMixin") && Launch.classLoader
                    .getResource("club/sk1er/patcher/hooks/FontRendererHook.class") != null;
        }
        if (mixinClassName.startsWith("pit12.feature.patch.resourceleakfix.mixin.")) {
            return optifine;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        ClassNode mixin = mixinInfo.getClassNode(
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Map<String, Integer> expectedCalls = new LinkedHashMap<>();
        for (MethodNode method : mixin.methods) {
            AnnotationNode injector = InjectionInfo.getInjectorAnnotation(mixinInfo, method);
            if (injector == null) {
                continue;
            }
            int required = Annotations.getValue(injector, "require", -1);
            if (required == 0) {
                continue;
            }
            expectedCalls.put(handlerSignature.apply(mixinClassName, method),
                    Math.max(1, Math.max(required, Annotations.getValue(injector, "expect", 1))));
        }
        List<String> missing = missingInjections(targetClass, expectedCalls);
        if (!missing.isEmpty()) {
            recordFailure(mixinInfo.getName());
            LOGGER.warn("Mixin {} has missing injections in {}: {}", mixinClassName,
                    targetClassName, String.join(", ", missing));
        }
    }

    private static List<String> missingInjections(ClassNode targetClass,
            Map<String, Integer> expectedCalls) {
        List<String> missing = new ArrayList<>();
        if (expectedCalls.isEmpty()) {
            return missing;
        }
        Map<String, Integer> actualCalls = new HashMap<>();
        for (MethodNode method : targetClass.methods) {
            if (expectedCalls.containsKey(method.name + method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction =
                    method.instructions.getFirst(); instruction != null; instruction =
                            instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(targetClass.name)) {
                    String handler = call.name + call.desc;
                    if (expectedCalls.containsKey(handler)) {
                        actualCalls.merge(handler, 1, Integer::sum);
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> expected : expectedCalls.entrySet()) {
            int actual = actualCalls.getOrDefault(expected.getKey(), 0);
            if (actual < expected.getValue()) {
                missing.add(expected.getKey() + " (expected at least " + expected.getValue()
                        + ", found " + actual + ")");
            }
        }
        return missing;
    }

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable failure, IMixinInfo mixin,
            ErrorAction action) {
        if (config.getPlugin() instanceof MixinConfigPlugin) {
            recordFailure(mixin.getName());
        }
        return action;
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable failure, IMixinInfo mixin,
            ErrorAction action) {
        if (mixin.getConfig().getPlugin() instanceof MixinConfigPlugin) {
            recordFailure(mixin.getName());
        }
        return action;
    }
}
