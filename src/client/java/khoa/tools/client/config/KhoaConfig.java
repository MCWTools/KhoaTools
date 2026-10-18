package khoa.tools.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import khoa.tools.KhoaTools;

/** config/khoatools.json, one section per feature. */
public final class KhoaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static JsonObject root = new JsonObject();

	private KhoaConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("khoatools.json");
	}

	public static synchronized void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) {
				root = parsed.getAsJsonObject();
			}
		} catch (Exception e) {
			KhoaTools.LOGGER.error("Could not read khoatools.json, using defaults", e);
		}
	}

	public static synchronized JsonObject section(String name) {
		JsonElement existing = root.get(name);
		if (existing != null && existing.isJsonObject()) {
			return existing.getAsJsonObject();
		}
		JsonObject created = new JsonObject();
		root.add(name, created);
		return created;
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			KhoaTools.LOGGER.error("Could not save khoatools.json", e);
		}
	}

	// ---- small typed helpers so features/settings do not touch Json directly ----

	public static synchronized int getInt(String sectionName, String key, int fallback) {
		JsonObject section = section(sectionName);
		try {
			return section.has(key) ? section.get(key).getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	public static synchronized double getDouble(String sectionName, String key, double fallback) {
		JsonObject section = section(sectionName);
		try {
			return section.has(key) ? section.get(key).getAsDouble() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	public static synchronized void putDouble(String sectionName, String key, double value) {
		section(sectionName).addProperty(key, value);
		save();
	}

	public static synchronized boolean getBool(String sectionName, String key, boolean fallback) {
		JsonObject section = section(sectionName);
		try {
			return section.has(key) ? section.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	public static synchronized String getString(String sectionName, String key, String fallback) {
		JsonObject section = section(sectionName);
		try {
			return section.has(key) ? section.get(key).getAsString() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	public static synchronized void put(String sectionName, String key, int value) {
		section(sectionName).addProperty(key, value);
		save();
	}

	public static synchronized void put(String sectionName, String key, boolean value) {
		section(sectionName).addProperty(key, value);
		save();
	}

	public static synchronized void put(String sectionName, String key, String value) {
		section(sectionName).addProperty(key, value);
		save();
	}
}
