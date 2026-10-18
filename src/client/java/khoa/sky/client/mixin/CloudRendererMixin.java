package khoa.sky.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import khoa.tools.KhoaTools;
import khoa.sky.client.CloudReloader;
import khoa.sky.client.SkyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Vanilla reads textures/environment/clouds.png in CloudRenderer.prepare().
 * We only redirect that one stream to the selected file, so cloud geometry/shaders stay vanilla.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin implements CloudReloader {
	// These are the compiler-generated bridge methods (the real ones use the private TextureData type).
	@Shadow
	protected abstract Object prepare(ResourceManager resourceManager, ProfilerFiller profiler);

	@Shadow
	protected abstract void apply(Object prepared, ResourceManager resourceManager, ProfilerFiller profiler);

	@Inject(method = "<init>", at = @At("TAIL"))
	private void skychanger$register(CallbackInfo ci) {
		SkyManager.setCloudReloader(this);
	}

	@WrapOperation(
		method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;")
	)
	private InputStream skychanger$openClouds(ResourceManager manager, Identifier id, Operation<InputStream> original) throws IOException {
		Path custom = SkyManager.customCloudPath();
		if (custom != null) {
			try {
				return Files.newInputStream(custom);
			} catch (IOException e) {
				KhoaTools.LOGGER.error("Could not open custom clouds {}", custom, e);
			}
		}
		return original.call(manager, id);
	}

	@Override
	public void skychanger$reloadClouds() {
		ResourceManager manager = Minecraft.getInstance().getResourceManager();
		this.apply(this.prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
	}
}
