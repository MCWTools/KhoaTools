package khoa.sky.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import khoa.tools.KhoaTools;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the selected PNGs lazily on the render thread and hands them to the mixins.
 * Nothing here touches shaders or pipelines - it only supplies textures and vertex buffers.
 */
public final class SkyManager {
	private SkyManager() {}

	private static final String UNLOADED = "\u0000unloaded";

	private static final class Entry {
		String loadedName = UNLOADED;
		SkyResource resource;
	}

	private static final Map<SkySlot, Entry> ENTRIES = new EnumMap<>(SkySlot.class);

	/** Set by CloudRendererMixin so we can re-read the cloud texture without a full resource reload. */
	private static CloudReloader cloudReloader;

	static {
		for (SkySlot slot : SkySlot.values()) {
			ENTRIES.put(slot, new Entry());
		}
	}

	public static void setCloudReloader(CloudReloader reloader) {
		cloudReloader = reloader;
	}

	// ---- used by the render mixins (render thread) ----

	/**
	 * Loads / swaps the texture of a slot if the selection changed.
	 * Must be called while NO render pass is open (GPU uploads are not allowed inside a pass),
	 * i.e. at the start of the vanilla sky method, before it creates its pass.
	 */
	public static void prepare(SkySlot slot) {
		Entry entry = ENTRIES.get(slot);
		String wanted = SkyConfig.get(slot);
		if (!Objects.equals(entry.loadedName, wanted)) {
			if (entry.resource != null) {
				entry.resource.close();
				entry.resource = null;
			}
			entry.loadedName = wanted; // set first so a broken file is not retried every frame
			if (wanted != null) {
				entry.resource = load(slot, wanted);
			}
		}
	}

	/** Already-loaded resource of a slot, or null for "Default". Never uploads anything. */
	public static SkyResource peek(SkySlot slot) {
		Entry entry = ENTRIES.get(slot);
		return Objects.equals(entry.loadedName, SkyConfig.get(slot)) ? entry.resource : null;
	}

	/** Path of the selected cloud texture, or null for vanilla clouds. */
	public static Path customCloudPath() {
		String name = SkyConfig.get(SkySlot.OVERWORLD_CLOUDS);
		if (name == null) {
			return null;
		}
		Path path = SkyFolders.resolve(SkySlot.OVERWORLD_CLOUDS, name);
		return Files.isRegularFile(path) ? path : null;
	}

	// ---- changes from the GUI ----

	/** Called after the config for a slot changed. */
	public static void onSelectionChanged(SkySlot slot) {
		if (slot == SkySlot.OVERWORLD_CLOUDS) {
			if (cloudReloader != null) {
				try {
					cloudReloader.skychanger$reloadClouds();
				} catch (Exception e) {
					KhoaTools.LOGGER.error("Could not reload clouds", e);
				}
			}
		}
		// Textures of the other slots are swapped lazily on the next frame (see get()).
	}

	/** Forces every slot to be re-read from disk. */
	public static void reloadAll() {
		for (Entry entry : ENTRIES.values()) {
			entry.loadedName = UNLOADED;
		}
		onSelectionChanged(SkySlot.OVERWORLD_CLOUDS);
	}

	// ---- loading ----

	private static SkyResource load(SkySlot slot, String name) {
		Path path = SkyFolders.resolve(slot, name);
		if (!Files.isRegularFile(path)) {
			KhoaTools.LOGGER.warn("Selected image is missing: {}", path);
			return null;
		}
		NativeImage image = null;
		try (InputStream in = Files.newInputStream(path)) {
			image = NativeImage.read(in);
			int w = image.getWidth();
			int h = image.getHeight();
			String label = "SkyChanger " + slot.key() + "/" + name;
			switch (slot) {
				case OVERWORLD_SKYBOX, END_SKYBOX -> {
					if (w < 3 || h < 2) {
						throw new IllegalArgumentException("skybox image is too small (" + w + "x" + h + ")");
					}
					if (w % 3 != 0 || h % 2 != 0 || w / 3 != h / 2) {
						KhoaTools.LOGGER.warn("{} is {}x{}; a skybox should be 3 x 2 square tiles (e.g. 1536x1024)", name, w, h);
					}
					SkyTexture texture = new SkyTexture(label, image, true);
					image = null; // owned by the texture now
					GpuBuffer buffer = SkyMeshes.buildSkyboxCube(w, h);
					return new SkyResource(texture, buffer);
				}
				case OVERWORLD_SUN -> {
					SkyTexture texture = new SkyTexture(label, image, w > 64);
					image = null;
					return new SkyResource(texture, SkyMeshes.buildSun());
				}
				case OVERWORLD_MOON -> {
					boolean sheet = w == h * 2;
					SkyTexture texture = new SkyTexture(label, image, w > 64);
					image = null;
					return new SkyResource(texture, SkyMeshes.buildMoon(sheet, w, h));
				}
				default -> {
					return null;
				}
			}
		} catch (Exception e) {
			KhoaTools.LOGGER.error("Could not load {}", path, e);
			return null;
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}
}
