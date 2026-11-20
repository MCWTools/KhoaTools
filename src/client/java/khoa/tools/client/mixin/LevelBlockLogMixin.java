package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import khoa.tools.client.feature.blocklog.BlockLogFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Block Log: every block change on the client (server updates and your own predicted breaks / placements) passes here. */
@Mixin(Level.class)
public abstract class LevelBlockLogMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
	private void khoa$blockLog(BlockPos pos, BlockState newState, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
		if (!BlockLogFeature.recording()) return;
		Level self = (Level) (Object) this;
		if (!self.isClientSide() || !self.hasChunkAt(pos)) return;
		BlockLogFeature.onBlockChange(pos, self.getBlockState(pos), newState);
	}
}
