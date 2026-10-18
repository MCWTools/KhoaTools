package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;

import khoa.tools.client.feature.lightlevel.LightLevelOverlayFeature;

/** Light Level Overlay: every block update on the client level triggers a rebuild of the overlay. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelLightMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void khoatools$lightBlockUpdated(CallbackInfo ci) {
		LightLevelOverlayFeature.markDirty();
	}
}
