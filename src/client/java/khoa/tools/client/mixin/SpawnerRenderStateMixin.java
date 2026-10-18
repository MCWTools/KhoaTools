package khoa.tools.client.mixin;

import khoa.tools.client.feature.optimizefps.SpawnerEggHolder;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SpawnerRenderState.class)
public class SpawnerRenderStateMixin implements SpawnerEggHolder {
	@Unique
	private ItemStackRenderState khoa$egg;

	@Override
	public ItemStackRenderState khoa$getEgg() {
		return khoa$egg;
	}

	@Override
	public void khoa$setEgg(ItemStackRenderState egg) {
		this.khoa$egg = egg;
	}
}
