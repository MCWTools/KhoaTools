package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import khoa.tools.client.feature.camera.CameraWatch;

/** Freezes player movement (walk, jump, sneak, sprint) while watching a saved camera. */
@Mixin(KeyboardInput.class)
public abstract class CameraWatchInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void khoatools$freeze(CallbackInfo ci) {
		if (CameraWatch.isWatching()) {
			KeyboardInput self = (KeyboardInput) (Object) this;
			self.keyPresses = Input.EMPTY;
			((ClientInputAccessor) self).khoatools$setMoveVector(Vec2.ZERO);
		}
	}
}
