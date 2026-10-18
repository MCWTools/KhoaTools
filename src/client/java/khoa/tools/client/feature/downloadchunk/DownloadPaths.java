package khoa.tools.client.feature.downloadchunk;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;

final class DownloadPaths {
	private DownloadPaths() {
	}

	/** .minecraft/khoatools/downloadedchunk */
	static Path root(Minecraft mc) {
		return mc.gameDirectory.toPath().resolve("khoatools").resolve("downloadedchunk");
	}
}
