package khoa.tools.client.feature.chathighlight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import khoa.tools.client.chat.ChatMarker;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Colors YOUR name wherever someone else mentions it in chat (only the name, not the whole message) and plays a sound.
 * Matches your full name, the first part of it ("Khoa" for "Khoa_Code_Play") and the extra keywords from the settings.
 * Your own messages are skipped (tick "Also in my own messages" to test). Colors are applied to new messages and to old
 * messages restored by Better Chat; the sound only for new messages.
 */
public final class ChatHighlightFeature implements KhoaFeature {
	private static final String[] SEPARATORS = {": ", "\u00BB ", "\u203A ", "> ", "| ", "\u27A4 ", "\u2192 ", "- "};
	private static final Pattern LEADING_TAGS = Pattern.compile("^(\\[[^\\]]*\\]\\s*)*");
	private static final int SOUND_COOLDOWN_TICKS = 10;

	private static boolean on;
	private static String lastName;
	private static int lastSound = -1000;

	@Override
	public String id() {
		return ChatHighlightConfig.SECTION;
	}

	@Override
	public String displayName() {
		return "Chat Highlight";
	}

	@Override
	public String description() {
		return "Colors your name when someone mentions it in chat (only the name, not the whole message) and plays a sound. "
			+ "Also matches the first part of your name (Khoa for Khoa_Code_Play) and the extra keywords below. "
			+ "Works on old chat restored by Better Chat too. Click the card to turn it on/off.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.choice("color", "Mention color", "Color of your name when mentioned.",
				ChatHighlightConfig.COLOR_NAMES, ChatHighlightConfig::color, ChatHighlightConfig::setColor),
			Setting.bool("bold", "Bold", "Make the mentioned name bold.", ChatHighlightConfig::bold, ChatHighlightConfig::setBold),
			Setting.string("keywords", "Extra keywords", "Other words that count as a mention, separated by commas (nicknames).",
				ChatHighlightConfig::keywords, ChatHighlightConfig::setKeywords),
			Setting.bool("own", "Also in my own messages", "Color your name in your own messages too (to test the feature).",
				ChatHighlightConfig::own, ChatHighlightConfig::setOwn),
			Setting.slider("sound_volume", "Alert volume", "Mention sound volume (0-100%).", 0, 100,
				() -> Math.round(ChatHighlightConfig.soundVolume() * 100), v -> ChatHighlightConfig.setSoundVolume(v / 100.0)));
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
		KhoaFeature.say(mc, "Chat Highlight: " + (on ? "on" : "off"));
	}

	private static String playerName() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			lastName = mc.player.getName().getString();
		}
		return lastName;
	}

	/** Index where the message text starts (after "<Name> ", "Name: ", "Name \u00BB "), or 0 when the line has no sender shape. */
	static int senderEnd(String text) {
		if (text.startsWith("<")) {
			int close = text.indexOf("> ");
			return close > 0 && close < 48 ? close + 2 : 0;
		}
		int best = 0;
		String head = text.substring(0, Math.min(text.length(), 56));
		for (String separator : SEPARATORS) {
			int at = head.indexOf(separator);
			if (at > 0 && (best == 0 || at + separator.length() < best)) {
				best = at + separator.length();
			}
		}
		return best;
	}

	/** "Khoa_Code_Play" -> "Khoa", "KhoaCodePlay" -> "Khoa", "xX_Pro_Xx" -> null (parts shorter than 4 letters are too common). */
	static String firstPart(String name) {
		int end = 1;
		while (end < name.length()) {
			char c = name.charAt(end);
			if (c == '_' || Character.isDigit(c) || (Character.isUpperCase(c) && Character.isLowerCase(name.charAt(end - 1)))) {
				break;
			}
			end++;
		}
		String part = name.substring(0, end);
		return part.length() < 4 || part.equalsIgnoreCase(name) ? null : part;
	}

	private static Pattern wordPattern(List<String> words) {
		List<String> sorted = new ArrayList<>(words);
		sorted.sort(Comparator.comparingInt(String::length).reversed());
		List<String> quoted = new ArrayList<>();
		for (String word : sorted) {
			quoted.add(Pattern.quote(word));
		}
		return Pattern.compile("(?<![\\p{L}\\p{N}_])(?:" + String.join("|", quoted) + ")(?![\\p{L}\\p{N}_])",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/** Pure matching logic (no Minecraft calls) so it can be tested on its own. Null = nothing to color. */
	static boolean[] findMarks(String text, String name, String keywords, boolean includeOwn) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		List<String> words = new ArrayList<>();
		words.add(name);
		String part = firstPart(name);
		if (part != null) {
			words.add(part);
		}
		for (String keyword : keywords.split(",")) {
			String trimmed = keyword.strip();
			if (trimmed.length() >= 2 && !words.contains(trimmed)) {
				words.add(trimmed);
			}
		}
		Pattern pattern = wordPattern(words);

		int start = senderEnd(text);
		int from;
		if (start > 0) {
			if (start >= text.length()) {
				return null;
			}
			if (!includeOwn && wordPattern(List.of(name)).matcher(text.substring(0, start)).find()) {
				return null; // your own message
			}
			from = start;
		} else {
			// no "Name: text" shape (plugin formats, server messages): a mention is anywhere except at the very start of the line
			Matcher tags = LEADING_TAGS.matcher(text);
			from = tags.find() ? tags.end() : 0;
		}

		Matcher matcher = pattern.matcher(text);
		boolean[] mark = new boolean[text.length()];
		boolean found = false;
		int searchFrom = from;
		while (searchFrom <= text.length() && matcher.find(searchFrom)) {
			if (start == 0 && !includeOwn && matcher.start() <= from) {
				searchFrom = matcher.end(); // "Khoa joined the game"
				continue;
			}
			found = true;
			for (int i = matcher.start(); i < matcher.end(); i++) {
				mark[i] = true;
			}
			searchFrom = Math.max(matcher.end(), searchFrom + 1);
		}
		return found ? mark : null;
	}

	private static boolean[] marksFor(String text) {
		String name = playerName();
		return findMarks(text, name, ChatHighlightConfig.keywords(), ChatHighlightConfig.own());
	}

	/** Returns the same component with your name colored, or the original when nothing matches. */
	public static Component apply(Component content) {
		if (!on) {
			return content;
		}
		String text = content.getString();
		boolean[] mark = marksFor(text);
		if (mark == null) {
			return content;
		}
		return ChatMarker.paint(content, text, mark, ChatHighlightConfig.colorFormat(), ChatHighlightConfig.bold(), false);
	}

	/** Called once for every NEW message from the server (not for restored old chat): plays the mention sound. */
	public static void onServerMessage(Component content) {
		if (!on || ChatHighlightConfig.soundVolume() <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui == null || marksFor(content.getString()) == null) {
			return;
		}
		int now = mc.gui.getGuiTicks();
		if (now >= lastSound && now - lastSound < SOUND_COOLDOWN_TICKS) {
			return; // several mentions in a burst = one sound
		}
		lastSound = now;
		mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), ChatHighlightConfig.soundVolume(), 1.2F);
	}
}
