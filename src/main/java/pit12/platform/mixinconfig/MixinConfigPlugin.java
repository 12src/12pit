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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import pit12.platform.lunar.LunarSupport;

public final class MixinConfigPlugin implements IMixinConfigPlugin, IMixinErrorHandler {
    // Mixin creates a separate instance for error callbacks.
    private static final Set<String> FAILED_MIXINS = new ConcurrentSkipListSet<>();
    private boolean lunar;

    public static List<String> failedMixinNames() {
        return new ArrayList<>(FAILED_MIXINS);
    }

    @Override
    public void onLoad(String mixinPackage) {
        Mixins.registerErrorHandlerClass(MixinConfigPlugin.class.getName());
        lunar = LunarSupport.isLoaded();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName
                .equals("pit12.feature.render.nametag.mixin.PatcherRendererLivingEntityMixin")) {
            return !lunar && Launch.classLoader
                    .getResource("club/sk1er/patcher/config/PatcherConfig.class") != null;
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
                return !mixinClassName.endsWith(".FontRendererHookMixin") && Launch.classLoader
                        .getResource("optifine/OptiFineClassTransformer.class") != null;
            }
            return !mixinClassName.endsWith(".LunarFontRendererMixin") && Launch.classLoader
                    .getResource("club/sk1er/patcher/hooks/FontRendererHook.class") != null;
        }
        if (mixinClassName.startsWith("pit12.feature.patch.resourceleakfix.mixin.")
                || mixinClassName
                        .equals("pit12.platform.mixin.rendering.OptiFineFontRendererMixin")) {
            // Forge detects OptiFine after Mixin preparation.
            return Launch.classLoader
                    .getResource("optifine/OptiFineClassTransformer.class") != null;
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
            IMixinInfo mixinInfo) {}

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable failure, IMixinInfo mixin,
            ErrorAction action) {
        if (config.getPlugin() instanceof MixinConfigPlugin) {
            FAILED_MIXINS.add(mixin.getName());
        }
        return action;
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable failure, IMixinInfo mixin,
            ErrorAction action) {
        if (mixin.getConfig().getPlugin() instanceof MixinConfigPlugin) {
            FAILED_MIXINS.add(mixin.getName());
        }
        return action;
    }
}
