package khoa.tools.client.feature.betterchat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;

import khoa.tools.KhoaTools;
import khoa.tools.client.chat.ChatComponentExt;
import khoa.tools.client.feature.chathighlight.ChatHighlightFeature;
import khoa.tools.client.util.ServerKey;

/**
 * Per-server chat history on disk: .minecraft/khoatools/betterchat/&lt;server&gt;.json
 * Messages are stored as component JSON so colors, bold, hover/click etc. survive.
 * Only messages sent by the server are stored (never client-side messages).
 */
public final class ChatCache {
	private static final Gson GSON = new GsonBuilder().create();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "khoatools-betterchat-io");
		thread.setDaemon(true);
		return thread;
	});

	private static final ArrayDeque<JsonElement> MESSAGES = new ArrayDeque<>();
	private static String key;
	private static String serverName;
	private static boolean dirty;
	private static int ticks;

	private ChatCache() {
	}

	private static Path file(Minecraft mc, String key) {
		return mc.gameDirectory.toPath().resolve("khoatools").resolve("betterchat").resolve(key + ".json");
	}

	private static RegistryOps<JsonElement> ops(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		return connection == null ? null : connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	/** Joined a server / world. */
	public static void onJoin(Minecraft mc) {
		key = ServerKey.id(mc);
		serverName = ServerKey.display(mc);
		MESSAGES.clear();
		dirty = false;
		if (BetterChatConfig.cache()) {
			loadFromDisk(mc);
			replay(mc);
		}
	}

	/** Left the server. */
	public static void onDisconnect() {
		flush(false);
		MESSAGES.clear();
		key = null;
		serverName = null;
	}

	/** The cache option was switched at runtime. */
	public static void onCacheToggled(Minecraft mc, boolean enabled) {
		if (key == null) {
			return;
		}
		if (enabled) {
			MESSAGES.clear();
			loadFromDisk(mc); // keep history that is already on disk, new messages get appended
		} else {
			flush(false);
			MESSAGES.clear();
		}
	}

	/** Called for every server message that is added to the chat. */
	public static void record(Component message) {
		if (key == null || !BetterChatConfig.cache()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		RegistryOps<JsonElement> ops = ops(mc);
		if (ops == null) {
			return;
		}
		ComponentSerialization.CODEC.encodeStart(ops, message).result().ifPresent(json -> {
			MESSAGES.addLast(json);
			trimMemory();
			dirty = true;
		});
	}

	public static void tick() {
		if (dirty && ++ticks >= 100) { // every ~5 seconds
			flush(true);
		}
	}

	private static void trimMemory() {
		int max = BetterChatConfig.effectiveLength();
		while (MESSAGES.size() > max) {
			MESSAGES.removeFirst();
		}
	}

	private static void loadFromDisk(Minecraft mc) {
		MESSAGES.clear();
		Path file = file(mc, key);
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement element : root.getAsJsonArray("messages")) {
				MESSAGES.addLast(element);
			}
			trimMemory();
		} catch (Exception e) {
			KhoaTools.LOGGER.error("Could not read chat cache {}", file, e);
		}
	}

	/** Show the cached chat again, followed by a highlighted separator line. */
	private static void replay(Minecraft mc) {
		RegistryOps<JsonElement> ops = ops(mc);
		if (ops == null || MESSAGES.isEmpty()) {
			return;
		}
		ChatComponent chat = mc.gui.getChat();
		// old addedTime => the restored lines do not pop up on the HUD, they are just in the history
		int oldTime = mc.gui.getGuiTicks() - 1_000_000;
		int restored = 0;
		for (JsonElement json : new ArrayList<>(MESSAGES)) {
			Component component = ComponentSerialization.CODEC.parse(ops, json).result().orElse(null);
			if (component == null) {
				continue;
			}
			((ChatComponentExt) chat).khoatools$addRestored(
				new GuiMessage(oldTime, ChatHighlightFeature.apply(component), null, GuiMessageSource.SYSTEM_SERVER, null));
			restored++;
		}
		if (restored > 0) {
			chat.addClientSystemMessage(Component.literal("\u2550\u2550\u2550\u2550 \u25B2 OLD CHAT  |  NEW CHAT \u25BC \u2550\u2550\u2550\u2550")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
	}

	/** Write the current history to disk. */
	public static void flush(boolean async) {
		if (key == null || !BetterChatConfig.cache() || !dirty) {
			ticks = 0;
			return;
		}
		ticks = 0;
		dirty = false;

		Minecraft mc = Minecraft.getInstance();
		Path file = file(mc, key);
		JsonObject root = new JsonObject();
		root.addProperty("server", serverName);
		JsonArray array = new JsonArray();
		MESSAGES.forEach(array::add);
		root.add("messages", array);

		Runnable write = () -> {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
			} catch (IOException e) {
				KhoaTools.LOGGER.error("Could not write chat cache {}", file, e);
			}
		};
		if (async) {
			IO.execute(write);
		} else {
			try {
				IO.submit(write).get();
			} catch (InterruptedException | ExecutionException e) {
				KhoaTools.LOGGER.error("Chat cache write interrupted", e);
			}
		}
	}

	/** Used when a message limit shrinks. */
	public static void onLengthChanged() {
		trimMemory();
		dirty = true;
	}
}
