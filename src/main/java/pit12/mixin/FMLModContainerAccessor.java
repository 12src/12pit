package pit12.mixin;

import java.util.Map;
import net.minecraftforge.fml.common.FMLModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FMLModContainer.class, remap = false)
public interface FMLModContainerAccessor {
    @Accessor(value = "descriptor", remap = false)
    Map<String, Object> pit12$getDescriptor();
}
