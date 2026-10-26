package pit12.platform.mixinconfig;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import pit12.platform.lunar.LunarSupport;

public final class MixinConfigPlugin implements IMixinConfigPlugin {
    private boolean lunar;
    private boolean optifine;
    private boolean pylon;

    @Override
    public void onLoad(String mixinPackage) {
        lunar = LunarSupport.isLoaded();
        pylon = FabricLoader.getInstance().isModLoaded("pylon");
        optifine = getClass().getClassLoader()
                .getResource("optifine/OptiFineClassTransformer.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("pit12.platform.mixin.pylon."))
            return pylon;
        if (mixinClassName.equals("pit12.platform.mixin.feature.nametag.VanillaRendererLivingEntityMixin"))
            return !lunar;
        if (mixinClassName.equals("pit12.platform.mixin.feature.nametag.LunarEntityLivingBaseMixin"))
            return lunar;
        return optifine || !mixinClassName.startsWith("pit12.platform.mixin.feature.resourceleakfix.");
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
}
