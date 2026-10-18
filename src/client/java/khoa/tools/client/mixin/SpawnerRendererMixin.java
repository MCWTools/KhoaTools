package khoa.tools.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnerRenderer.class)
public class SpawnerRendererMixin {
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
