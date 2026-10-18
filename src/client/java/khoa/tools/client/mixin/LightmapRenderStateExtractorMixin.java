package khoa.tools.client.mixin;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

import khoa.tools.client.feature.fullbright.FullbrightFeature;

/** Fullbright: after vanilla fills the lightmap state, force the night-vision term to full white. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
	@Shadow
	private boolean needsUpdate;

	@Inject(method = "extract", at = @At("HEAD"))
	private void khoa$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		// Recompute on every call so toggling off restores vanilla values immediately too.
		this.needsUpdate = true;
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void khoa$applyFullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (!FullbrightFeature.enabled()) {
			return;
		}

		state.brightness = Math.max(state.brightness, 1.0F);
		state.nightVisionEffectIntensity = 1.0F;
		state.nightVisionColor = new Vector3f(1.0F, 1.0F, 1.0F);
		state.darknessEffectScale = 0.0F;
		state.bossOverlayWorldDarkening = 0.0F;
		state.needsUpdate = true;
	}
}
