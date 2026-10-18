package khoa.tools.client.feature.chatsearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import khoa.tools.client.chat.ChatComponentExt;
import khoa.tools.client.chat.ChatMarker;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.chathighlight.ChatHighlightConfig;
import khoa.tools.setting.Setting;

/**
 * /khoatoolsclient chatsearch &lt;text...&gt;  opens the chat scrolled to the newest line that contains the whole text
 * (case-insensitive). If no line has the whole text it falls back to lines containing single words, best match first.
 * The searched text is colored/underlined in ALL chat lines (including old Better Chat history) until the next search.
 * Running the same search again jumps to the next older match. /khoatoolsclient chatsearch (no text) clears the highlight.
 */
public final class ChatSearchFeature implements KhoaFeature {
	private static final String SECTION = "chatsearch";

	private static Pattern pattern;
	private static String lastQuery = "";
	private static GuiMessage lastTarget;
	private static GuiMessage pending;
	private static int stage;

	@Override
	public String id() {
		return SECTION;
	}

	@Override
	public String displayName() {
		return "Chat Search";
	}

	@Override
	public String description() {
		return "Type /khoatoolsclient chatsearch <text> in chat: the chat opens scrolled to the newest line with that text and the text is highlighted "
			+ "in all lines (also old chat). No exact match = lines with single words. Same search again = next older match. "
			+ "Green card = a highlight is active; click the card to clear it.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.choice("color", "Search color", "Color of the searched text (it is also underlined).",
			ChatHighlightConfig.COLOR_NAMES, () -> KhoaConfig.getInt(SECTION, "color", 2), value -> KhoaConfig.put(SECTION, "color", value)));
	}

	@Override
	public boolean isActive() {
		return pattern != null;
	}

	@Override
	public void activate(Minecraft mc) {
		clear(mc);
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(ChatSearchFeature::tick);
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.executes(ctx -> {
				clear(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
				search(ctx.getSource().getClient(), StringArgumentType.getString(ctx, "text"));
				return 1;
			})));
	}

	// ---- used by GuiMessageMixin when a chat line is wrapped for display ----

	public static Component highlight(Component content) {
		Pattern current = pattern;
		if (current == null) {
			return content;
		}
		String text = content.getString();
		Matcher matcher = current.matcher(text);
		boolean[] mark = new boolean[text.length()];
		boolean found = false;
		while (matcher.find()) {
			if (matcher.end() == matcher.start()) {
				continue;
			}
			found = true;
			for (int i = matcher.start(); i < matcher.end(); i++) {
				mark[i] = true;
			}
		}
		if (!found) {
			return content;
		}
		return ChatMarker.paint(content, text, mark, ChatHighlightConfig.colorAt(KhoaConfig.getInt(SECTION, "color", 2)), false, true);
	}

	// ---- search ----

	private static void clear(Minecraft mc) {
		pattern = null;
		lastQuery = "";
		lastTarget = null;
		pending = null;
		if (mc.gui != null) {
			((ChatComponentExt) mc.gui.getChat()).khoatools$refresh();
			mc.gui.setOverlayMessage(Component.literal("Chat Search: cleared"), false);
		}
	}

	private static Pattern compile(List<String> terms) {
		List<String> quoted = new ArrayList<>();
		terms.stream().sorted(Comparator.comparingInt(String::length).reversed()).forEach(term -> quoted.add(Pattern.quote(term)));
		return Pattern.compile(String.join("|", quoted), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	private static boolean contains(String text, String term) {
		return Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
	}

	private static void search(Minecraft mc, String raw) {
		String query = raw.strip().replaceAll("\\s+", " ");
		if (query.isEmpty()) {
			clear(mc);
			return;
		}
		ChatComponentExt ext = (ChatComponentExt) mc.gui.getChat();
		List<GuiMessage> all = ext.khoatools$messages(); // newest first

		// 1) whole text
		List<GuiMessage> hits = new ArrayList<>();
		Pattern whole = compile(List.of(query));
		for (GuiMessage message : all) {
			if (whole.matcher(message.content().getString()).find()) {
				hits.add(message);
			}
		}
		List<String> terms = List.of(query);
		boolean wordMode = false;

		// 2) single words, lines with the most different words first (newest first on ties)
		if (hits.isEmpty()) {
			List<String> words = List.of(query.split(" ")).stream().distinct().toList();
			if (words.size() > 1) {
				List<GuiMessage> scored = new ArrayList<>();
				List<Integer> scores = new ArrayList<>();
				for (GuiMessage message : all) {
					String text = message.content().getString();
					int score = 0;
					for (String word : words) {
						if (contains(text, word)) {
							score++;
						}
					}
					if (score > 0) {
						scored.add(message);
						scores.add(score);
					}
				}
				List<Integer> order = new ArrayList<>();
				for (int i = 0; i < scored.size(); i++) {
					order.add(i);
				}
				order.sort((a, b) -> Integer.compare(scores.get(b), scores.get(a))); // stable: keeps newest first
				for (int i : order) {
					hits.add(scored.get(i));
				}
				terms = words;
				wordMode = true;
			}
		}

		if (hits.isEmpty()) {
			pattern = null;
			lastQuery = "";
			lastTarget = null;
			ext.khoatools$refresh();
			mc.gui.setOverlayMessage(Component.literal("Chat Search: nothing found"), false);
			return;
		}

		int index = 0;
		String key = query.toLowerCase(Locale.ROOT);
		if (key.equals(lastQuery) && lastTarget != null) {
			for (int i = 0; i < hits.size(); i++) {
				if (hits.get(i) == lastTarget) {
					index = (i + 1) % hits.size();
					break;
				}
			}
		}
		lastQuery = key;
		lastTarget = hits.get(index);
		pattern = compile(terms);
		ext.khoatools$refresh(); // wraps every line again, now with the highlight (or without the old one)
		pending = lastTarget;
		stage = 1;
		mc.gui.setOverlayMessage(Component.literal("Chat Search: match " + (index + 1) + "/" + hits.size() + (wordMode ? " (single words)" : "")), false);
	}

	/** Vanilla resets the chat scroll right after a command was sent, so open + scroll happen on the next ticks. */
	private static void tick(Minecraft mc) {
		if (pending == null || mc.gui == null) {
			return;
		}
		ChatComponent chat = mc.gui.getChat();
		if (stage == 1) {
			if (!(mc.screen instanceof ChatScreen)) {
				chat.openScreen(ChatComponent.ChatMethod.MESSAGE, ChatScreen::new);
			}
			stage = 2;
			return;
		}
		boolean scrolled = ((ChatComponentExt) chat).khoatools$scrollTo(pending);
		pending = null;
		if (!scrolled) {
			mc.gui.setOverlayMessage(Component.literal("Chat Search: that line is not in the visible chat"), false);
		}
	}
}
