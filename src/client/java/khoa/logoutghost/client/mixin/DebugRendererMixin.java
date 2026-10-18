package khoa.logoutghost.client.mixin;

import khoa.logoutghost.client.ghost.GhostManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
	/** Runs every frame inside the gizmo collector, regardless of F3+B. */
	@Inject(method = "emitGizmos", at = @At("TAIL"))
	private void logoutghost$ghostHitboxes(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
		GhostManager.emitHitboxes(partialTick);
	}
}
