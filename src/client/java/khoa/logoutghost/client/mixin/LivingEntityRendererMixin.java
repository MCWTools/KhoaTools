package khoa.logoutghost.client.mixin;

import khoa.logoutghost.client.ghost.GhostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla draws "invisible but visible to you" entities with a fixed 15% white tint (ARGB 654311423).
 * For ghosts we replace that constant with our own fading alpha.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	/** Alpha of the entity currently being submitted, or -1 when it is not a ghost. */
	private static float logoutghost$currentAlpha = -1.0F;

	@Inject(method = SUBMIT, at = @At("HEAD"))
	private void logoutghost$begin(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
								   net.minecraft.client.renderer.SubmitNodeCollector collector,
								   net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar) {
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			logoutghost$currentAlpha = GhostManager.alphaFor(avatar.id, partial);
		} else {
			logoutghost$currentAlpha = -1.0F;
		}
	}

	@Inject(method = SUBMIT, at = @At("RETURN"))
	private void logoutghost$end(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
								 net.minecraft.client.renderer.SubmitNodeCollector collector,
								 net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		logoutghost$currentAlpha = -1.0F;
	}

	@ModifyConstant(method = SUBMIT, constant = @Constant(intValue = 654311423))
	private int logoutghost$ghostTint(int original) {
		float a = logoutghost$currentAlpha;
		if (a < 0.0F) {
			return original;
		}
		int ia = Math.max(0, Math.min(255, Math.round(a * 255.0F)));
		return (ia << 24) | 0x00FFFFFF;
	}
}
