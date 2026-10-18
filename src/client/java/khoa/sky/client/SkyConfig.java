package khoa.sky.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import khoa.tools.KhoaTools;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent selection stored in config/skychanger.json. */
public final class SkyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Data {
		Map<String, String> selected = new LinkedHashMap<>();
		boolean overworldStars = true;
		boolean overworldSunriseGlow = true;
	}

	private static Data data = new Data();

	private SkyConfig() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("skychanger.json");
	}

	public static void load() {
		Path file = file();
		if (Files.exists(file)) {
			try {
				Data loaded = GSON.fromJson(Files.readString(file), Data.class);
				if (loaded != null) {
					if (loaded.selected == null) {
						loaded.selected = new LinkedHashMap<>();
					}
					data = loaded;
				}
			} catch (Exception e) {
				KhoaTools.LOGGER.error("Could not read {}", file, e);
			}
		}
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(data));
		} catch (IOException e) {
			KhoaTools.LOGGER.error("Could not write {}", file(), e);
		}
	}

	/** @return selected png name, or null for "Default". */
	public static String get(SkySlot slot) {
		return data.selected.get(slot.key());
	}

	public static void set(SkySlot slot, String name) {
		if (name == null) {
			data.selected.remove(slot.key());
		} else {
			data.selected.put(slot.key(), name);
		}
		save();
	}

	public static boolean overworldStars() {
		return data.overworldStars;
	}

	public static void setOverworldStars(boolean value) {
		data.overworldStars = value;
		save();
	}

	public static boolean overworldSunriseGlow() {
		return data.overworldSunriseGlow;
	}

	public static void setOverworldSunriseGlow(boolean value) {
		data.overworldSunriseGlow = value;
		save();
	}
}
