package khoa.tools.client.playlist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import khoa.tools.KhoaTools;

/** Persistent client-side playlists and playback for sounds registered in Minecraft. */
public final class PlaylistManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String SECTION = "playlists";
	private static final List<Playlist> PLAYLISTS = new ArrayList<>();
	private static String activeId;
	private static SoundInstance currentSound;
	private static int currentIndex;
	private static int currentAge;
	private static boolean loaded;

	private PlaylistManager() {}

	/** True while a playlist is playing: vanilla background music is held back (see MusicManagerMixin). */
	public static boolean active() { return activeId != null && currentSound != null; }

	public enum Source { MINECRAFT, DEVICE }

	public record Track(Source source, String id, String title) {}

	public static final class Playlist {
		private final String id;
		private String name;
		private boolean shuffle;
		private boolean loop;
		private final List<Track> tracks = new ArrayList<>();

		private Playlist(String id, String name) { this.id = id; this.name = name; }
		public String id() { return id; }
		public String name() { return name; }
		public boolean shuffle() { return shuffle; }
		public boolean loop() { return loop; }
		public List<Track> tracks() { return List.copyOf(tracks); }
		public boolean active() { return id.equals(activeId); }
	}

	public static synchronized List<Playlist> playlists() { load(); return List.copyOf(PLAYLISTS); }
	public static synchronized Playlist find(String id) { load(); return PLAYLISTS.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }

	public static synchronized Playlist create(String name) {
		load();
		Playlist p = new Playlist(UUID.randomUUID().toString(), cleanName(name));
		PLAYLISTS.add(p); save(); return p;
	}

	public static synchronized void rename(String id, String name) {
		Playlist p = find(id); if (p != null) { p.name = cleanName(name); save(); }
	}

	public static synchronized void delete(String id) {
		Playlist p = find(id); if (p == null) return;
		if (p.active()) stop();
		PLAYLISTS.remove(p); save();
	}

	public static synchronized void setShuffle(String id, boolean value) { Playlist p = find(id); if (p != null) { p.shuffle = value; save(); } }
	public static synchronized void setLoop(String id, boolean value) { Playlist p = find(id); if (p != null) { p.loop = value; save(); } }

	public static synchronized void addTrack(String playlistId, Track track) {
		Playlist p = find(playlistId);
		if (p != null && p.tracks.stream().noneMatch(t -> t.source == track.source && t.id.equals(track.id))) { p.tracks.add(track); save(); }
	}

	public static synchronized void removeTrack(String playlistId, int index) {
		Playlist p = find(playlistId);
		if (p != null && index >= 0 && index < p.tracks.size()) {
			boolean wasPlaying = p.active() && currentIndex == index;
			p.tracks.remove(index);
			if (p.active() && index < currentIndex) currentIndex--;
			if (wasPlaying) { stop(); if (!p.tracks.isEmpty()) playAt(p, Math.min(index, p.tracks.size() - 1)); }
			save();
		}
	}

	public static synchronized void toggle(String id) {
		Playlist p = find(id); if (p == null) return;
		if (p.active()) { stop(); return; }
		stop(); activeId = id;
		if (!p.tracks.isEmpty()) playAt(p, 0);
		else save();
	}

	public static synchronized void next() {
		Playlist p = find(activeId); if (p == null || p.tracks.isEmpty()) { stop(); return; }
		int next = p.shuffle && p.tracks.size() > 1 ? RandomSource.create().nextInt(p.tracks.size()) : currentIndex + 1;
		if (next >= p.tracks.size()) { if (!p.loop) { stop(); return; } next = 0; }
		playAt(p, next);
	}

	public static synchronized void tick() {
		if (currentSound == null) return;
		Minecraft mc = Minecraft.getInstance();
		// The sound engine starts the sound asynchronously, so give it a second before treating "not active" as "finished".
		if (++currentAge < 20) return;
		if (!mc.getSoundManager().isActive(currentSound)) next();
	}

	public static List<Track> availableMinecraftTracks() {
		Minecraft mc = Minecraft.getInstance();
		Map<String, Track> tracks = new LinkedHashMap<>();
		// Real soundtrack files (vanilla and resource packs): assets/<ns>/sounds/music/**.ogg -> id "<ns>:music/..."
		mc.getResourceManager().listResources("sounds/music", id -> id.getPath().endsWith(".ogg")).keySet().forEach(id -> {
			String path = id.getPath().substring("sounds/".length(), id.getPath().length() - ".ogg".length());
			String trackId = id.getNamespace() + ":" + path;
			tracks.put(trackId, new Track(Source.MINECRAFT, trackId, prettify(path)));
		});
		if (mc.level != null) {
			mc.level.registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG).entrySet().stream()
				.map(entry -> {
					var song = entry.getValue();
					return new Track(Source.MINECRAFT, song.soundEvent().value().location().toString() + "|" + song.lengthInSeconds(), song.description().getString() + " (" + entry.getKey().identifier().getPath() + ")");
				}).forEach(track -> tracks.putIfAbsent(track.id(), track));
		}
		return tracks.values().stream().sorted(java.util.Comparator.comparing(Track::title)).toList();
	}

	private static String prettify(String value) {
		String name = value.substring(value.lastIndexOf('/') + 1).replace('_', ' ');
		String vanilla = switch (name) {
			case "calm1" -> "C418 - Minecraft";
			case "calm2" -> "C418 - Dry Hands";
			case "calm3" -> "C418 - Wet Hands";
			case "hal1" -> "C418 - Clark";
			case "hal2" -> "C418 - Sweden";
			case "hal3" -> "C418 - Subwoofer Lullaby";
			case "nuance1" -> "C418 - Haggstrom";
			case "nuance2" -> "C418 - Mice on Venus";
			case "piano1" -> "C418 - Danny";
			case "piano2" -> "C418 - Beginning 2";
			case "piano3" -> "C418 - Beginning";
			default -> null;
		};
		if (vanilla != null) return vanilla;
		return name.isEmpty() ? value : Character.toUpperCase(name.charAt(0)) + name.substring(1) + " (soundtrack)";
	}

	public static Path deviceDirectory() {
		return FabricLoader.getInstance().getGameDir().resolve("khoatools").resolve("custommusic");
	}

	/** Scan supported files. Vanilla has no MP3/OGG file-player API, so playback is only enabled for registered Minecraft tracks. */
	public static List<Track> availableDeviceTracks() {
		try {
			Files.createDirectories(deviceDirectory());
			try (var paths = Files.list(deviceDirectory())) {
				return paths.filter(Files::isRegularFile).filter(p -> {
					String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
					return n.endsWith(".ogg") || n.endsWith(".mp3");
				}).sorted().map(p -> new Track(Source.DEVICE, p.getFileName().toString(), p.getFileName().toString())).toList();
			}
		} catch (IOException e) { KhoaTools.LOGGER.error("Could not scan custom music folder", e); return List.of(); }
	}

	private static void playAt(Playlist p, int index) {
		if (index < 0 || index >= p.tracks.size()) { stop(); return; }
		Track track = p.tracks.get(index);
		if (track.source != Source.MINECRAFT) { currentIndex = index; currentSound = null; activeId = null; save(); return; }
		// "ns:music/..." = a sound file played directly; "event|seconds" = a jukebox song's sound event.
		boolean file = !track.id.contains("|");
		Identifier id = Identifier.tryParse(track.id.split("\\|", 2)[0]);
		if (id == null) { KhoaTools.LOGGER.warn("Unknown playlist sound {}", track.id); currentIndex = index; currentSound = null; save(); return; }
		Minecraft mc = Minecraft.getInstance();
		if (currentSound != null) mc.getSoundManager().stop(currentSound);
		mc.getMusicManager().stopPlaying();
		currentIndex = index;
		currentAge = 0;
		currentSound = new PlaylistSound(id, file);
		mc.getSoundManager().play(currentSound);
		save();
	}

	private static void stop() {
		if (currentSound != null) Minecraft.getInstance().getSoundManager().stop(currentSound);
		currentSound = null; activeId = null; currentIndex = 0; currentAge = 0; save();
	}

	private static String cleanName(String name) {
		String cleaned = name == null ? "" : name.strip();
		return cleaned.isEmpty() ? "Playlist" : cleaned.substring(0, Math.min(48, cleaned.length()));
	}

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("khoatools-playlists.json"); }

	private static synchronized void load() {
		if (loaded) return;
		loaded = true;
		if (!Files.exists(file())) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray list = root.getAsJsonArray("playlists");
			if (list != null) for (JsonElement element : list) {
				JsonObject obj = element.getAsJsonObject(); Playlist p = new Playlist(obj.get("id").getAsString(), obj.get("name").getAsString());
				p.shuffle = obj.has("shuffle") && obj.get("shuffle").getAsBoolean(); p.loop = obj.has("loop") && obj.get("loop").getAsBoolean();
				JsonArray tracks = obj.getAsJsonArray("tracks");
				if (tracks != null) for (JsonElement t : tracks) { JsonObject to = t.getAsJsonObject(); p.tracks.add(new Track(Source.valueOf(to.get("source").getAsString()), to.get("id").getAsString(), to.get("title").getAsString())); }
				PLAYLISTS.add(p);
			}
		} catch (Exception e) { KhoaTools.LOGGER.error("Could not load custom playlists", e); }
	}

	private static synchronized void save() {
		JsonObject root = new JsonObject(); JsonArray list = new JsonArray();
		for (Playlist p : PLAYLISTS) {
			JsonObject obj = new JsonObject(); obj.addProperty("id", p.id); obj.addProperty("name", p.name); obj.addProperty("shuffle", p.shuffle); obj.addProperty("loop", p.loop);
			JsonArray tracks = new JsonArray(); for (Track t : p.tracks) { JsonObject to = new JsonObject(); to.addProperty("source", t.source.name()); to.addProperty("id", t.id); to.addProperty("title", t.title); tracks.add(to); }
			obj.add("tracks", tracks); list.add(obj);
		}
		root.add(SECTION, list);
		try { Files.createDirectories(file().getParent()); Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8); }
		catch (IOException e) { KhoaTools.LOGGER.error("Could not save custom playlists", e); }
	}

	private static final class PlaylistSound extends AbstractSoundInstance {
		private final boolean file;

		PlaylistSound(Identifier soundId, boolean file) {
			super(soundId, SoundSource.MUSIC, RandomSource.create());
			this.file = file;
			this.looping = false; this.relative = true; this.volume = 1.0F; this.pitch = 1.0F;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		/** A music file is not a sound event, so build the one-sound event by hand (streamed, like vanilla music). */
		@Override public WeighedSoundEvents resolve(SoundManager manager) {
			if (!file) return super.resolve(manager);
			Sound s = new Sound(this.identifier, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, 16);
			WeighedSoundEvents events = new WeighedSoundEvents(this.identifier, null);
			events.addSound(s);
			this.sound = s;
			return events;
		}
	}
}
