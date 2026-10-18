package khoa.sky.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** A DynamicTexture with a clamp-to-edge sampler so skybox tiles don't wrap around. */
public final class SkyTexture extends DynamicTexture {
	public SkyTexture(String label, NativeImage image, boolean linear) {
		super(() -> label, image);
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
		this.upload();
	}
}
