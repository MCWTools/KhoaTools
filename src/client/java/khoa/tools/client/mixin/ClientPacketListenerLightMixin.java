package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;

import khoa.tools.client.feature.lightlevel.LightLevelOverlayFeature;

/** Light Level Overlay: light packets from the server and new chunks also trigger a rebuild. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerLightMixin {
	@Inject(method = "handleLightUpdatePacket", at = @At("TAIL"))
	private void khoatools$lightPacket(CallbackInfo ci) {
		LightLevelOverlayFeature.markDirty();
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
	private void khoatools$chunkPacket(CallbackInfo ci) {
		LightLevelOverlayFeature.markDirty();
	}
}
