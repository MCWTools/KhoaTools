package khoa.tools.client.mixin;

import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void khoa$animationHead(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay overlay,
		CallbackInfoReturnable<BlockEntityRenderState> cir) {
		BlockEntityRenderState[] out = new BlockEntityRenderState[1];
		if (OptimizeFpsFeature.blockEntityOverride(be, overlay, out)) cir.setReturnValue(out[0]);
	}

	@Inject(method = "tryExtractRenderState", at = @At("RETURN"))
	private void khoa$animationReturn(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay overlay,
		CallbackInfoReturnable<BlockEntityRenderState> cir) {
		OptimizeFpsFeature.blockEntityExtracted(be, cir.getReturnValue(), overlay);
	}
}
