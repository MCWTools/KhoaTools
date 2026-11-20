package khoa.tools.client.mixin;

import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
	/** Optimized Spawner: a fully enclosed spawner is meshed as a dark grey solid block instead of its see-through cage. */
	@Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
	private void khoa$enclosedSpawner(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
		BlockState state, BlockStateModel model, long seed, CallbackInfo ci) {
		if (!OptimizeFpsFeature.spawnerEnclosed(level, pos, state)) return;
		BlockState standIn = OptimizeFpsFeature.enclosedSpawnerStandIn();
		BlockStateModel standInModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(standIn);
		ci.cancel();
		((ModelBlockRenderer) (Object) this).tesselateBlock(output, x, y, z, level, pos, standIn, standInModel, seed);
	}
}
