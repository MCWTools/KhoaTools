package khoa.tools.client.feature.chattranslator;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import khoa.tools.client.chat.ChatComponentExt;
import khoa.tools.client.chat.ChatCoordinates;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.chathighlight.ChatHighlightFeature;
import khoa.tools.setting.Setting;

/**
 * Translates the TEXT of chat messages (never the sender part) with a small local model served by Ollama.
 * Flow: the message is shown as received -> language is guessed -> if it is not the target language the text goes to the model
 * -> the chat line is replaced by the translated one. Own messages and client messages are skipped.
 * /khoatoolsclient chattranslator            toggle
 * /khoatoolsclient chattranslator download   download the configured model through Ollama
 */
public final class ChatTranslatorFeature implements KhoaFeature {
	private static final int MAX_IN_FLIGHT = 8;
	private static final int MAX_LENGTH = 400;
	private static final String[] SEPARATORS = {": ", "\u00BB ", "\u203A ", "> "};

	private static final Map<String, String> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > 500;
		}
	});
	private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
	private static final AtomicBoolean PULLING = new AtomicBoolean();

	private static boolean on;
	private static boolean warned;

	@Override
	public String id() {
		return ChatTranslatorConfig.SECTION;
	}

	@Override
	public String displayName() {
		return "Chat Translator";
	}

	@Override
	public String description() {
		return "Translates chat messages (text only, not the player name) to the chosen language with a small local model. "
			+ "Needs Ollama running; press Download model once. Each message is shown first, then replaced by the translation. "
			+ "Click the card to turn it on/off.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.choice("target", "Translate to", "Language the chat is translated into. Lines already in it are left alone.",
				ChatTranslatorConfig.LANGUAGES, ChatTranslatorConfig::target, ChatTranslatorConfig::setTarget),
			Setting.string("model", "Model", "Ollama model used for translating (default " + ChatTranslatorConfig.DEFAULT_MODEL + ", about 2 GB).",
				ChatTranslatorConfig::model, ChatTranslatorConfig::setModel),
			Setting.action("download", "Download model", "Downloads the model above through Ollama (progress is shown in chat).",
				"Download", ChatTranslatorFeature::download),
			Setting.string("url", "Ollama address", "Where Ollama listens.", ChatTranslatorConfig::url, ChatTranslatorConfig::setUrl),
			Setting.bool("pluginformats", "Plugin chat formats", "Also translate servers whose chat looks like \"Rank Name: text\" or \"Name \u00BB text\".",
				ChatTranslatorConfig::pluginFormats, ChatTranslatorConfig::setPluginFormats));
	}

	@Override
	public boolean persistEnabled() {
		return true;
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		warned = false;
		KhoaFeature.say(mc, "Chat Translator: " + (on ? "on (-> " + ChatTranslatorConfig.targetName() + ")" : "off"));
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.executes(ctx -> {
				activate(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("download").executes(ctx -> {
				download();
				return 1;
			})));
	}

	// ---- model download ----

	private static void download() {
		Minecraft mc = Minecraft.getInstance();
		if (!PULLING.compareAndSet(false, true)) {
			KhoaFeature.say(mc, "Chat Translator: a download is already running");
			return;
		}
		KhoaFeature.say(mc, "Chat Translator: downloading " + ChatTranslatorConfig.model() + " through Ollama...");
		Thread thread = new Thread(() -> {
			try {
				OllamaClient.pull(text -> mc.execute(() -> KhoaFeature.say(mc, "Chat Translator: " + text)));
				mc.execute(() -> KhoaFeature.say(mc, "Chat Translator: model ready"));
			} catch (Exception e) {
				mc.execute(() -> KhoaFeature.say(mc, "Chat Translator: download failed - " + describe(e)));
			} finally {
				PULLING.set(false);
			}
		}, "khoatools-chattranslator-pull");
		thread.setDaemon(true);
		thread.start();
	}

	// ---- chat pipeline (called from ChatComponentMixin after a message was added) ----

	public static void onMessage(ChatComponent chat, GuiMessage added, Component content, MessageSignature signature,
		GuiMessageSource source, GuiMessageTag tag) {
		if (!on || source == GuiMessageSource.SYSTEM_CLIENT) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Split split = split(content);
		if (split == null || split.message.length() > MAX_LENGTH || !LangDetect.worthTranslating(split.message)) {
			return;
		}
		if (mc.player != null && split.prefixText.contains(mc.player.getName().getString())) {
			return; // own message
		}
		String target = ChatTranslatorConfig.targetCode();
		if (LangDetect.detect(split.message).equals(target)) {
			return;
		}

		int addedTime = mc.gui.getGuiTicks();
		String key = target + "\0" + split.message;
		String cached = CACHE.get(key);
		if (cached != null) {
			apply(chat, added, split, cached, signature, source, tag, addedTime);
			return;
		}
		if (IN_FLIGHT.get() >= MAX_IN_FLIGHT) {
			return; // too many waiting, leave this one untranslated
		}
		IN_FLIGHT.incrementAndGet();
		OllamaClient.translate(split.message, ChatTranslatorConfig.targetName()).whenComplete((result, error) -> {
			IN_FLIGHT.decrementAndGet();
			if (error != null) {
				mc.execute(() -> warn(error));
				return;
			}
			if (result == null || result.isBlank()) {
				return;
			}
			CACHE.put(key, result);
			mc.execute(() -> apply(chat, added, split, result, signature, source, tag, addedTime));
		});
	}

	private static void apply(ChatComponent chat, GuiMessage old, Split split, String translated, MessageSignature signature,
		GuiMessageSource source, GuiMessageTag tag, int addedTime) {
		if (!on || sameText(split.message, translated)) {
			return;
		}
		Component rebuilt = ChatHighlightFeature.apply(ChatCoordinates.makeClickable(split.rebuild(translated)));
		((ChatComponentExt) chat).khoatools$replace(old, new GuiMessage(addedTime, rebuilt, signature, source, tag));
	}

	private static boolean sameText(String a, String b) {
		return normalize(a).equals(normalize(b));
	}

	private static String normalize(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", "");
	}

	private static void warn(Throwable error) {
		if (warned) {
			return;
		}
		warned = true;
		KhoaFeature.say(Minecraft.getInstance(), "Chat Translator: " + describe(error));
	}

	private static String describe(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
			return "can't reach Ollama at " + ChatTranslatorConfig.url() + " - start Ollama first";
		}
		return String.valueOf(cause.getMessage());
	}

	// ---- splitting "sender part" from "message text" ----

	private record Seg(String text, Style style) {
	}

	private static final class Split {
		final List<Seg> prefix = new ArrayList<>();
		String prefixText = "";
		Style messageStyle = Style.EMPTY;
		String message = "";

		Component rebuild(String translated) {
			MutableComponent out = Component.empty();
			for (Seg seg : prefix) {
				out.append(Component.literal(seg.text()).withStyle(seg.style()));
			}
			out.append(Component.literal(translated).withStyle(messageStyle));
			return out;
		}
	}

	private static Split split(Component content) {
		List<Seg> segs = new ArrayList<>();
		StringBuilder full = new StringBuilder();
		content.visit((style, text) -> {
			if (!text.isEmpty()) {
				segs.add(new Seg(text, style));
				full.append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);

		String text = full.toString();
		int cut = findCut(text);
		if (cut <= 0 || cut >= text.length() || text.substring(cut).isBlank()) {
			return null;
		}

		Split split = new Split();
		boolean styleSet = false;
		int pos = 0;
		for (Seg seg : segs) {
			int end = pos + seg.text().length();
			if (end <= cut) {
				split.prefix.add(seg);
			} else {
				if (pos < cut) {
					split.prefix.add(new Seg(seg.text().substring(0, cut - pos), seg.style()));
				}
				if (!styleSet) {
					split.messageStyle = seg.style();
					styleSet = true;
				}
			}
			pos = end;
		}
		split.prefixText = text.substring(0, cut);
		split.message = text.substring(cut).strip();
		return split;
	}

	/** Index where the message text starts, or -1 when the line has no "sender, then text" shape. */
	private static int findCut(String text) {
		if (text.startsWith("<")) { // vanilla: <Name> text
			int close = text.indexOf("> ");
			return close > 0 && close < 48 ? close + 2 : -1;
		}
		if (!ChatTranslatorConfig.pluginFormats()) {
			return -1;
		}
		int best = -1;
		int limit = Math.min(text.length(), 56);
		String head = text.substring(0, limit);
		for (String separator : SEPARATORS) {
			int at = head.indexOf(separator);
			if (at > 0 && (best < 0 || at + separator.length() < best)) {
				best = at + separator.length();
			}
		}
		return best;
	}
}
