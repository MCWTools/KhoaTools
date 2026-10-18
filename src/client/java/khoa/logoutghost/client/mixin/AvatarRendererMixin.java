package khoa.logoutghost.client.mixin;

import khoa.logoutghost.client.ghost.GhostManager;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import khoa.logoutghost.client.ghost.GhostPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	/** Layers (armor, cape, items...) ignore the ghost tint, so skip them to keep the fade uniform. */
	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",
			at = @At("HEAD"), cancellable = true)
	private void logoutghost$noLayersForGhosts(AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (GhostManager.isGhostId(state.id)) {
			cir.setReturnValue(false);
		}
	}

	/** Keep the exact pose/animation the player had when they logged out (no limb reset, no idle sway drift). */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void logoutghost$freezeAnimation(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (avatar instanceof GhostPlayer g) {
			state.walkAnimationPos = g.frozenWalkPos;
			state.walkAnimationSpeed = g.frozenWalkSpeed;
			state.wornHeadAnimationPos = g.frozenWalkPos;
			state.attackTime = g.frozenAttackTime;
			state.ageInTicks = g.frozenAgeInTicks;
		}
	}
}
