package khoa.tools.client.mixin;

import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
	private static void khoa$spawnerFace(BlockState state, BlockState neighbor, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (OptimizeFpsFeature.cullSpawnerFace(state, neighbor)) cir.setReturnValue(false);
	}
}
