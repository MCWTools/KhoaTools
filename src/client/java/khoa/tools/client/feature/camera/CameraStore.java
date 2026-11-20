package khoa.tools.client.feature.camera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import khoa.tools.KhoaTools;
import khoa.tools.client.util.ServerKey;

/** Saved cameras, stored per server + dimension in config/khoatools-cameras.json. */
public final class CameraStore {
	public static final class Cam {
		public String name;
		public String server;
		public double x, y, z;
		public float yaw, pitch;

		Vec3 pos() {
			return new Vec3(x, y, z);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, List<Cam>> WORLDS = new LinkedHashMap<>();
	private static boolean loaded;

	private CameraStore() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("khoatools-cameras.json");
	}

	/** Key of the current server + dimension, or null when not in a world. */
	public static String worldKey(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) return null;
		return ServerKey.display(mc) + "|" + level.dimension().identifier();
	}

	private static synchronized void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		Path file = file();
		if (!Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				List<Cam> list = new ArrayList<>();
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					Cam c = new Cam();
					c.name = o.get("name").getAsString();
					c.server = o.has("server") ? o.get("server").getAsString() : "";
					c.x = o.get("x").getAsDouble();
					c.y = o.get("y").getAsDouble();
					c.z = o.get("z").getAsDouble();
					c.yaw = o.get("yaw").getAsFloat();
					c.pitch = o.get("pitch").getAsFloat();
					list.add(c);
				}
				WORLDS.put(e.getKey(), list);
			}
		} catch (Exception e) {
			KhoaTools.LOGGER.error("Could not read khoatools-cameras.json", e);
		}
	}

	private static synchronized void save() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<Cam>> e : WORLDS.entrySet()) {
			if (e.getValue().isEmpty()) continue;
			JsonArray arr = new JsonArray();
			for (Cam c : e.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", c.name);
				o.addProperty("server", c.server);
				o.addProperty("x", c.x);
				o.addProperty("y", c.y);
				o.addProperty("z", c.z);
				o.addProperty("yaw", c.yaw);
				o.addProperty("pitch", c.pitch);
				arr.add(o);
			}
			root.add(e.getKey(), arr);
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			KhoaTools.LOGGER.error("Could not save khoatools-cameras.json", e);
		}
	}

	/** All cameras of the current server + dimension (live list copy). */
	public static synchronized List<Cam> current(Minecraft mc) {
		ensureLoaded();
		String key = worldKey(mc);
		if (key == null) return List.of();
		return new ArrayList<>(WORLDS.getOrDefault(key, List.of()));
	}

	public static synchronized Cam find(Minecraft mc, String name) {
		for (Cam c : current(mc)) {
			if (c.name.equalsIgnoreCase(name)) return c;
		}
		return null;
	}

	/** Cameras of the current world whose chunk is inside the render distance of the player. */
	public static List<Cam> inRange(Minecraft mc) {
		List<Cam> out = new ArrayList<>();
		for (Cam c : current(mc)) {
			if (isInRange(mc, c.pos())) out.add(c);
		}
		return out;
	}

	public static boolean isInRange(Minecraft mc, Vec3 pos) {
		if (mc.player == null) return false;
		int rd = mc.options.getEffectiveRenderDistance();
		int dx = Math.abs(((int) Math.floor(pos.x) >> 4) - mc.player.chunkPosition().x());
		int dz = Math.abs(((int) Math.floor(pos.z) >> 4) - mc.player.chunkPosition().z());
		return Math.max(dx, dz) <= rd;
	}

	/** Returns an error message, or null on success. */
	public static synchronized String create(Minecraft mc, String name, Vec3 pos, float yaw, float pitch) {
		ensureLoaded();
		String key = worldKey(mc);
		if (key == null) return "Not in a world.";
		if (find(mc, name) != null) return "A camera named '" + name + "' already exists here.";
		Cam c = new Cam();
		c.name = name;
		c.server = ServerKey.display(mc);
		set(c, pos, yaw, pitch);
		WORLDS.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
		save();
		return null;
	}

	public static synchronized String move(Minecraft mc, String name, Vec3 pos, float yaw, float pitch) {
		Cam c = find(mc, name);
		if (c == null) return "No camera named '" + name + "' in this world.";
		set(c, pos, yaw, pitch);
		save();
		return null;
	}

	public static synchronized String delete(Minecraft mc, String name) {
		Cam c = find(mc, name);
		if (c == null) return "No camera named '" + name + "' in this world.";
		WORLDS.getOrDefault(worldKey(mc), new ArrayList<>()).remove(c);
		save();
		return null;
	}

	private static void set(Cam c, Vec3 pos, float yaw, float pitch) {
		c.x = pos.x;
		c.y = pos.y;
		c.z = pos.z;
		c.yaw = yaw;
		c.pitch = pitch;
	}
}
