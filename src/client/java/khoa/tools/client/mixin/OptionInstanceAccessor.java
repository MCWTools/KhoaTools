package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.OptionInstance;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("value")
	void khoa$setValue(Object value);
}
