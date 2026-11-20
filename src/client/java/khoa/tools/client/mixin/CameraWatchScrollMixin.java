package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import khoa.tools.client.feature.camera.CameraWatch;

/** In freecam the mouse wheel changes the flying speed instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class CameraWatchScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void khoatools$scrollSpeed(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (CameraWatch.isFree() && Minecraft.getInstance().screen == null) {
			CameraWatch.scroll(yOffset);
			ci.cancel();
		}
	}
}
