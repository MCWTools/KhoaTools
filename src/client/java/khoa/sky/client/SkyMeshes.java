package khoa.sky.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Builds the vertex buffers for the skybox cube and for sun / moon quads. */
public final class SkyMeshes {
	private SkyMeshes() {}

	private static final float S = 100.0F; // same half-size as vanilla's End sky cube
	private static final int WHITE = 0xFFFFFFFF;

	// Tile positions (column, row) in the 3x2 skybox image:
	//   Bottom | Top   | South
	//   West   | North | East
	private enum Face {
		BOTTOM(0, 0, new float[][]{{-S, -S, -S}, {S, -S, -S}, {S, -S, S}, {-S, -S, S}}),
		TOP(1, 0, new float[][]{{-S, S, S}, {S, S, S}, {S, S, -S}, {-S, S, -S}}),
		SOUTH(2, 0, new float[][]{{S, S, S}, {-S, S, S}, {-S, -S, S}, {S, -S, S}}),
		WEST(0, 1, new float[][]{{-S, S, S}, {-S, S, -S}, {-S, -S, -S}, {-S, -S, S}}),
		NORTH(1, 1, new float[][]{{-S, S, -S}, {S, S, -S}, {S, -S, -S}, {-S, -S, -S}}),
		EAST(2, 1, new float[][]{{S, S, -S}, {S, S, S}, {S, -S, S}, {S, -S, -S}});

		final int col;
		final int row;
		/** Corners as seen from inside the cube: top-left, top-right, bottom-right, bottom-left. */
		final float[][] corners;

		Face(int col, int row, float[][] corners) {
			this.col = col;
			this.row = row;
			this.corners = corners;
		}
	}

	/** 6 quads (24 vertices) with POSITION_TEX_COLOR, drawn with 36 indices like vanilla's end sky. */
	public static GpuBuffer buildSkyboxCube(int imageWidth, int imageHeight) {
		float tileW = imageWidth / 3.0F;
		float tileH = imageHeight / 2.0F;
		float insetU = 0.5F / imageWidth; // half a texel, avoids bleeding between tiles
		float insetV = 0.5F / imageHeight;
		VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * format.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
			for (Face face : Face.values()) {
				float u0 = face.col * tileW / imageWidth + insetU;
				float u1 = (face.col + 1) * tileW / imageWidth - insetU;
				float v0 = face.row * tileH / imageHeight + insetV;
				float v1 = (face.row + 1) * tileH / imageHeight - insetV;
				float[][] c = face.corners;
				float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
				int[] order = {0, 1, 2, 3};
				if (pointsOutward(c)) {
					order = new int[]{0, 3, 2, 1}; // flip so the front face looks into the cube
				}
				for (int i : order) {
					builder.addVertex(c[i][0], c[i][1], c[i][2]).setUv(uv[i][0], uv[i][1]).setColor(WHITE);
				}
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "SkyChanger skybox", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			}
		}
	}

	/** True if the quad (v0,v1,v2 winding) has its normal pointing away from the cube centre. */
	private static boolean pointsOutward(float[][] c) {
		float ax = c[1][0] - c[0][0], ay = c[1][1] - c[0][1], az = c[1][2] - c[0][2];
		float bx = c[2][0] - c[0][0], by = c[2][1] - c[0][1], bz = c[2][2] - c[0][2];
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		float cx = (c[0][0] + c[1][0] + c[2][0] + c[3][0]) / 4.0F;
		float cy = (c[0][1] + c[1][1] + c[2][1] + c[3][1]) / 4.0F;
		float cz = (c[0][2] + c[1][2] + c[2][2] + c[3][2]) / 4.0F;
		return nx * cx + ny * cy + nz * cz > 0.0F;
	}

	/** One sun quad, UV layout identical to vanilla's buildCelestialQuad. */
	public static GpuBuffer buildSun() {
		VertexFormat format = DefaultVertexFormat.POSITION_TEX;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
			builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(0.0F, 0.0F);
			builder.addVertex(1.0F, 0.0F, -1.0F).setUv(1.0F, 0.0F);
			builder.addVertex(1.0F, 0.0F, 1.0F).setUv(1.0F, 1.0F);
			builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(0.0F, 1.0F);
			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "SkyChanger sun", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			}
		}
	}

	/**
	 * Eight moon quads (one per MoonPhase, in enum order), like vanilla's buildMoonPhases.
	 * If {@code sheet} is true the image is a 4x2 phase sheet, otherwise every phase uses the whole image.
	 */
	public static GpuBuffer buildMoon(boolean sheet, int imageWidth, int imageHeight) {
		VertexFormat format = DefaultVertexFormat.POSITION_TEX;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(8 * 4 * format.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
			for (int i = 0; i < 8; i++) {
				float u0 = 0.0F, u1 = 1.0F, v0 = 0.0F, v1 = 1.0F;
				if (sheet) {
					int col = i % 4;
					int row = i / 4;
					float insetU = 0.5F / imageWidth;
					float insetV = 0.5F / imageHeight;
					u0 = col / 4.0F + insetU;
					u1 = (col + 1) / 4.0F - insetU;
					v0 = row / 2.0F + insetV;
					v1 = (row + 1) / 2.0F - insetV;
				}
				builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(u1, v1);
				builder.addVertex(1.0F, 0.0F, -1.0F).setUv(u0, v1);
				builder.addVertex(1.0F, 0.0F, 1.0F).setUv(u0, v0);
				builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(u1, v0);
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "SkyChanger moon", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			}
		}
	}
}
