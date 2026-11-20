package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import khoa.tools.client.feature.camera.CameraWatch;

/** While watching a saved camera, moves the render camera to its spot after vanilla aligned it with the player. */
@Mixin(Camera.class)
public abstract class CameraWatchCameraMixin {
	@Shadow private boolean detached;

	@Shadow protected abstract void setPosition(Vec3 position);

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void khoatools$watchCamera(float partialTicks, CallbackInfo ci) {
		CameraWatch.frame(net.minecraft.client.Minecraft.getInstance());
		if (!CameraWatch.isWatching()) return;
		this.detached = true;
		this.setRotation(CameraWatch.yaw(), CameraWatch.pitch());
		this.setPosition(CameraWatch.pos());
	}
}
