package khoa.sky.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;

/** A custom texture plus the vertex buffer that goes with it. */
public record SkyResource(AbstractTexture texture, GpuBuffer buffer) implements AutoCloseable {
	@Override
	public void close() {
		buffer.close();
		texture.close();
	}
}
