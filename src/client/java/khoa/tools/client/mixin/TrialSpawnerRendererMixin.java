package khoa.tools.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import khoa.tools.client.feature.optimizefps.SpawnerEggHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.TrialSpawnerRenderer;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** extractSpawnerData is shared by SpawnerRenderer and TrialSpawnerRenderer, so this one hook covers both. */
@Mixin(TrialSpawnerRenderer.class)
public class TrialSpawnerRendererMixin {
	@Inject(method = "extractSpawnerData", at = @At("HEAD"), cancellable = true)
	private static void khoa$egg(SpawnerRenderState state, float partialTick, Entity entity, EntityRenderDispatcher dispatcher,
		double oSpin, double spin, CallbackInfo ci) {
		if (entity == null || !OptimizeFpsFeature.spawner2dModel()) {
			return;
		}
		ci.cancel(); // no mob model extraction at all; displayEntity stays null so vanilla submit draws nothing
		state.displayEntity = null;
		state.spin = (float) Mth.lerp((double) partialTick, oSpin, spin) * 10.0F;
		((SpawnerEggHolder) (Object) state).khoa$setEgg(OptimizeFpsFeature.spawnEggFor(entity.getType()));
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SpawnerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"), cancellable = true)
	private void khoa$submitEgg(SpawnerRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (OptimizeFpsFeature.spawnerHidden(state)) {
			ci.cancel();
			return;
		}
		OptimizeFpsFeature.submitSpawnerEgg(state, pose, collector);
	}
}
