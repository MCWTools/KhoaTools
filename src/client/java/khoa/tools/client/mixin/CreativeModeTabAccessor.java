package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Lets custom item tabs (added at runtime) be placed on a creative inventory page, and refreshes the cached icon. */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
	@Mutable
	@Accessor("row")
	void khoa$setRow(CreativeModeTab.Row row);

	@Mutable
	@Accessor("column")
	void khoa$setColumn(int column);

	@Mutable
	@Accessor("displayName")
	void khoa$setDisplayName(net.minecraft.network.chat.Component name);

	@Accessor("iconItemStack")
	void khoa$setIconItemStack(ItemStack stack);
}
