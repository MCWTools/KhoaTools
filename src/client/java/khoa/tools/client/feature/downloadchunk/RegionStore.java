package khoa.tools.client.feature.downloadchunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Opens (and caches) vanilla RegionFile objects under
 * {@code <root>/<dimension folder>/region|entities/r.X.Z.mca}.
 * Open/lookup from the client thread only; writes happen on the IO thread.
 */
final class RegionStore {
	static final String REGION = "region";
	static final String ENTITIES = "entities";

	private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	private final Path root;
	private final String worldName;
	private final Map<Path, RegionFile> open = new HashMap<>();

	RegionStore(Path root, String worldName) {
		this.root = root;
		this.worldName = worldName;
	}

	RegionFile get(ResourceKey<Level> dimension, String folderName, ChunkPos pos) throws IOException {
		Path folder = DimensionType.getStorageFolder(dimension, root).resolve(folderName);
		Files.createDirectories(folder);
		Path file = folder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
		RegionFile existing = open.get(file);
		if (existing != null) {
			return existing;
		}
		RegionStorageInfo info = new RegionStorageInfo(worldName, dimension, folderName.equals(REGION) ? "chunk" : "entities");
		RegionFile created = new RegionFile(info, file, folder, false);
		open.put(file, created);
		return created;
	}

	/** Removes all cached region files from this store and returns them so the caller can close them. */
	List<RegionFile> drain() {
		List<RegionFile> files = new ArrayList<>(open.values());
		open.clear();
		return files;
	}

	/** Packed positions of every chunk already stored in this dimension's region folder (does not create anything). */
	Set<Long> listChunks(ResourceKey<Level> dimension) throws IOException {
		Set<Long> out = new HashSet<>();
		Path folder = DimensionType.getStorageFolder(dimension, root).resolve(REGION);
		if (!Files.isDirectory(folder)) {
			return out;
		}
		List<Path> files;
		try (Stream<Path> stream = Files.list(folder)) {
			files = stream.toList();
		}
		for (Path file : files) {
			Matcher m = REGION_FILE.matcher(file.getFileName().toString());
			if (!m.matches() || Files.size(file) < 8192) {
				continue;
			}
			int rx = Integer.parseInt(m.group(1));
			int rz = Integer.parseInt(m.group(2));
			RegionFile region;
			try {
				region = get(dimension, REGION, new ChunkPos(rx << 5, rz << 5));
			} catch (IOException e) {
				continue;
			}
			synchronized (region) {
				for (int lz = 0; lz < 32; lz++) {
					for (int lx = 0; lx < 32; lx++) {
						ChunkPos pos = new ChunkPos((rx << 5) + lx, (rz << 5) + lz);
						if (region.hasChunk(pos)) {
							out.add(pos.pack());
						}
					}
				}
			}
		}
		return out;
	}
}
