package khoa.logoutghost.client.ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import khoa.tools.KhoaTools;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Simple JSON config stored in config/logout-ghost.json. */
public final class GhostConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 0 = mod disabled, -1 = no fade (ghost stays), N > 0 = fade duration in ticks. */
	public int fadeTicks = 100;
	/** Starting opacity in percent (1-100). */
	public int opacityPercent = 50;

	/** Draw the ghost's hitbox outline (always, no F3+B needed). */
	public boolean showHitbox = true;

	private static GhostConfig instance = new GhostConfig();

	public static GhostConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("logout-ghost.json");
	}

	public static void load() {
		Path p = file();
		if (Files.exists(p)) {
			try {
				GhostConfig loaded = GSON.fromJson(Files.readString(p), GhostConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				KhoaTools.LOGGER.warn("Could not read config, using defaults", e);
			}
		}
		if (instance.fadeTicks < -1) {
			instance.fadeTicks = -1;
		}
		instance.opacityPercent = Math.max(1, Math.min(100, instance.opacityPercent));
		save();
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(instance));
		} catch (IOException e) {
			KhoaTools.LOGGER.warn("Could not save config", e);
		}
	}

	public float baseAlpha() {
		return Math.max(1, Math.min(100, opacityPercent)) / 100.0F;
	}
}
