package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import khoa.tools.client.feature.camera.CameraWatch;

/** Freecam is look-only: no attacking, using or picking blocks / entities. */
@Mixin(Minecraft.class)
public abstract class CameraWatchMinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void khoatools$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (CameraWatch.isFree()) cir.setReturnValue(false);
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void khoatools$noUse(CallbackInfo ci) {
		if (CameraWatch.isFree()) ci.cancel();
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void khoatools$noContinue(boolean down, CallbackInfo ci) {
		if (CameraWatch.isFree()) ci.cancel();
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void khoatools$noPick(CallbackInfo ci) {
		if (CameraWatch.isFree()) ci.cancel();
	}
}
