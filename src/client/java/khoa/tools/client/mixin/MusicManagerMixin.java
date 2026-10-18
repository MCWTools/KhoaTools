package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;

import khoa.tools.client.playlist.PlaylistManager;

/** While a custom playlist plays, vanilla background music does not start. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void khoa$holdBackMusic(CallbackInfo ci) {
		if (PlaylistManager.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
	private void khoa$holdBackStart(Music music, CallbackInfo ci) {
		if (PlaylistManager.active()) {
			ci.cancel();
		}
	}
}
