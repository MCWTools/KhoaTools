package khoa.tools.client.mixin;

import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void khoa$optimizeFps(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (OptimizeFpsFeature.isEntityHidden(entity) || khoa.tools.client.feature.bettertnt.BetterTntFeature.isTntHidden(entity)) cir.setReturnValue(false);
	}
}
