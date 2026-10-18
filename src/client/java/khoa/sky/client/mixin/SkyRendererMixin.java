package khoa.sky.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import khoa.sky.client.SkyConfig;
import khoa.sky.client.SkyManager;
import khoa.sky.client.SkyResource;
import khoa.sky.client.SkySlot;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps textures / vertex buffers inside vanilla's own sky methods.
 * We never create a pipeline or touch a shader, so shader packs keep seeing the normal
 * END_SKY / CELESTIAL draw calls and keep working.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	/** Non-null only while we re-use renderEndSky() to draw the overworld skybox. */
	@Unique
	private static SkyResource skychanger$overworldCube;

	@Shadow
	public abstract void renderEndSky();

	// ------------------------------------------------------------------ skybox

	// Textures are loaded at the HEAD of each vanilla method, before the method opens its render pass.
	@Inject(method = "renderSkyDisc", at = @At("HEAD"))
	private void skychanger$prepareOverworldSkybox(int skyColor, CallbackInfo ci) {
		SkyManager.prepare(SkySlot.OVERWORLD_SKYBOX);
	}

	@Inject(method = "renderEndSky", at = @At("HEAD"))
	private void skychanger$prepareEndSkybox(CallbackInfo ci) {
		if (skychanger$overworldCube == null) {
			SkyManager.prepare(SkySlot.END_SKYBOX);
		}
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	private void skychanger$prepareSunMoon(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
		SkyManager.prepare(SkySlot.OVERWORLD_SUN);
		SkyManager.prepare(SkySlot.OVERWORLD_MOON);
	}

	/** Overworld: right after the sky-coloured dome, draw our cube through vanilla's end-sky path. */
	@Inject(method = "renderSkyDisc", at = @At("TAIL"))
	private void skychanger$drawOverworldSkybox(int skyColor, CallbackInfo ci) {
		SkyResource cube = SkyManager.peek(SkySlot.OVERWORLD_SKYBOX);
		if (cube == null) {
			return;
		}
		skychanger$overworldCube = cube;
		try {
			this.renderEndSky();
		} finally {
			skychanger$overworldCube = null;
		}
	}

	@Unique
	private static SkyResource skychanger$currentCube() {
		// While the override is set we are drawing the overworld; otherwise renderEndSky() was called for The End.
		return skychanger$overworldCube != null ? skychanger$overworldCube : SkyManager.peek(SkySlot.END_SKYBOX);
	}

	@WrapOperation(
		method = "renderEndSky",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;getTextureView()Lcom/mojang/blaze3d/textures/GpuTextureView;")
	)
	private GpuTextureView skychanger$endSkyView(AbstractTexture texture, Operation<GpuTextureView> original) {
		SkyResource cube = skychanger$currentCube();
		return cube != null ? cube.texture().getTextureView() : original.call(texture);
	}

	@WrapOperation(
		method = "renderEndSky",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;getSampler()Lcom/mojang/blaze3d/textures/GpuSampler;")
	)
	private GpuSampler skychanger$endSkySampler(AbstractTexture texture, Operation<GpuSampler> original) {
		SkyResource cube = skychanger$currentCube();
		return cube != null ? cube.texture().getSampler() : original.call(texture);
	}

	// 180 = Opcodes.GETFIELD
	@WrapOperation(
		method = "renderEndSky",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/SkyRenderer;endSkyBuffer:Lcom/mojang/blaze3d/buffers/GpuBuffer;", opcode = 180)
	)
	private GpuBuffer skychanger$endSkyBuffer(SkyRenderer self, Operation<GpuBuffer> original) {
		SkyResource cube = skychanger$currentCube();
		return cube != null ? cube.buffer() : original.call(self);
	}

	// --------------------------------------------------------------------- sun

	@WrapOperation(
		method = "renderSun",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;getTextureView()Lcom/mojang/blaze3d/textures/GpuTextureView;")
	)
	private GpuTextureView skychanger$sunView(TextureAtlas atlas, Operation<GpuTextureView> original) {
		SkyResource sun = SkyManager.peek(SkySlot.OVERWORLD_SUN);
		return sun != null ? sun.texture().getTextureView() : original.call(atlas);
	}

	@WrapOperation(
		method = "renderSun",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;getSampler()Lcom/mojang/blaze3d/textures/GpuSampler;")
	)
	private GpuSampler skychanger$sunSampler(TextureAtlas atlas, Operation<GpuSampler> original) {
		SkyResource sun = SkyManager.peek(SkySlot.OVERWORLD_SUN);
		return sun != null ? sun.texture().getSampler() : original.call(atlas);
	}

	@WrapOperation(
		method = "renderSun",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/SkyRenderer;sunBuffer:Lcom/mojang/blaze3d/buffers/GpuBuffer;", opcode = 180)
	)
	private GpuBuffer skychanger$sunBuffer(SkyRenderer self, Operation<GpuBuffer> original) {
		SkyResource sun = SkyManager.peek(SkySlot.OVERWORLD_SUN);
		return sun != null ? sun.buffer() : original.call(self);
	}

	// -------------------------------------------------------------------- moon

	@WrapOperation(
		method = "renderMoon",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;getTextureView()Lcom/mojang/blaze3d/textures/GpuTextureView;")
	)
	private GpuTextureView skychanger$moonView(TextureAtlas atlas, Operation<GpuTextureView> original) {
		SkyResource moon = SkyManager.peek(SkySlot.OVERWORLD_MOON);
		return moon != null ? moon.texture().getTextureView() : original.call(atlas);
	}

	@WrapOperation(
		method = "renderMoon",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;getSampler()Lcom/mojang/blaze3d/textures/GpuSampler;")
	)
	private GpuSampler skychanger$moonSampler(TextureAtlas atlas, Operation<GpuSampler> original) {
		SkyResource moon = SkyManager.peek(SkySlot.OVERWORLD_MOON);
		return moon != null ? moon.texture().getSampler() : original.call(atlas);
	}

	@WrapOperation(
		method = "renderMoon",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/SkyRenderer;moonBuffer:Lcom/mojang/blaze3d/buffers/GpuBuffer;", opcode = 180)
	)
	private GpuBuffer skychanger$moonBuffer(SkyRenderer self, Operation<GpuBuffer> original) {
		SkyResource moon = SkyManager.peek(SkySlot.OVERWORLD_MOON);
		return moon != null ? moon.buffer() : original.call(self);
	}

	// ------------------------------------------------- optional overworld toggles

	@Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
	private void skychanger$toggleStars(float brightness, PoseStack poseStack, CallbackInfo ci) {
		if (!SkyConfig.overworldStars()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
	private void skychanger$toggleSunriseGlow(PoseStack poseStack, float sunAngle, int color, CallbackInfo ci) {
		if (!SkyConfig.overworldSunriseGlow()) {
			ci.cancel();
		}
	}
}
