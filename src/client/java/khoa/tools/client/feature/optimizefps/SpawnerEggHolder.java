package khoa.tools.client.feature.optimizefps;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Implemented by SpawnerRenderState (through a mixin): the spawn egg model that replaces the mob inside a spawner. */
public interface SpawnerEggHolder {
	ItemStackRenderState khoa$getEgg();

	void khoa$setEgg(ItemStackRenderState egg);
}
