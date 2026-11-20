package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import khoa.tools.client.feature.camera.CameraWatch;

/** While watching a saved camera the mouse turns the camera (if rotateable) instead of the frozen player. */
@Mixin(Entity.class)
public abstract class CameraWatchTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void khoatools$watchTurn(double yRot, double xRot, CallbackInfo ci) {
		if (CameraWatch.isWatching() && (Object) this instanceof LocalPlayer) {
			CameraWatch.turn(yRot, xRot);
			ci.cancel();
		}
	}
}
