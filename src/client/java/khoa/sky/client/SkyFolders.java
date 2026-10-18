package khoa.sky.client;

import khoa.tools.KhoaTools;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Creates and reads the .minecraft/skyboxes folder tree. */
public final class SkyFolders {
	private SkyFolders() {}

	public static Path root() {
		return FabricLoader.getInstance().getGameDir().resolve("skyboxes");
	}

	public static Path dir(SkySlot slot) {
		return root().resolve(slot.dimensionDir).resolve(slot.kindDir);
	}

	/** Creates every sub-folder and drops a layout template next to them. */
	public static void ensure() {
		try {
			for (SkySlot slot : SkySlot.values()) {
				Files.createDirectories(dir(slot));
			}
			Path template = root().resolve("skybox_template_layout.png");
			if (Files.notExists(template)) {
				try (InputStream in = SkyFolders.class.getResourceAsStream("/assets/khoatools/skybox_template.png")) {
					if (in != null) {
						Files.copy(in, template, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			Path readme = root().resolve("README.txt");
			if (Files.notExists(readme)) {
				Files.writeString(readme, README);
			}
		} catch (IOException e) {
			KhoaTools.LOGGER.error("Could not create skyboxes folder", e);
		}
	}

	/** Sorted *.png file names inside the slot's folder. */
	public static List<String> list(SkySlot slot) {
		List<String> result = new ArrayList<>();
		Path dir = dir(slot);
		if (Files.isDirectory(dir)) {
			try (Stream<Path> stream = Files.list(dir)) {
				stream.filter(Files::isRegularFile)
					.map(p -> p.getFileName().toString())
					.filter(n -> n.toLowerCase().endsWith(".png"))
					.sorted(Comparator.comparing(String::toLowerCase))
					.forEach(result::add);
			} catch (IOException e) {
				KhoaTools.LOGGER.error("Could not list {}", dir, e);
			}
		}
		return result;
	}

	public static Path resolve(SkySlot slot, String name) {
		return dir(slot).resolve(name);
	}

	private static final String README = """
		SkyChanger - how to use
		=======================

		Put .png files in these folders, then run /skychanger in game:

		  overworld/skybox/   6-face skybox, one image, 3 columns x 2 rows:
		                          Bottom | Top   | South
		                          West   | North | East
		                      (see skybox_template_layout.png in this folder.
		                       The "N (up)" mark on Bottom means the top edge of
		                       the Bottom tile points North.)
		  overworld/sun/      sun image (any size, square looks best)
		  overworld/moon/     moon image. A 2:1 image (width = 2 x height) is read
		                      as vanilla's 4x2 moon-phase sheet, anything else is
		                      used for every phase.
		  overworld/clouds/   cloud map, same format as vanilla clouds.png
		                      (non-transparent pixels = cloud)
		  the_end/skybox/     6-face skybox for The End (same layout as overworld)

		Tiles of a skybox should be square. Bigger = sharper (512 / 1024 per tile is fine).
		""";
}
