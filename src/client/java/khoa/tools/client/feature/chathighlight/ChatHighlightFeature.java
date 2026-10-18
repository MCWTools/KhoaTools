package khoa.tools.client.feature.chathighlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Colors YOUR name wherever someone else mentions it in chat (only the name, not the whole message).
 * Never touches the sender part of a line, so your own messages and "Khoa joined the game" are left alone.
 * Applied to new messages and to old messages restored by Better Chat.
 */
public final class ChatHighlightFeature implements KhoaFeature {
	private static final String[] SEPARATORS = {": ", "\u00BB ", "\u203A ", "> "};

	private static boolean on;
	private static String lastName;
	private static String lastAlertText;

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
		return "Colors your name when someone mentions it in chat (only the name, not the whole message), also in old chat restored by Better Chat. "
			+ "Click the card to turn it on/off.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.choice("color", "Mention color", "Color of your name when mentioned.",
				ChatHighlightConfig.COLOR_NAMES, ChatHighlightConfig::color, ChatHighlightConfig::setColor),
			Setting.bool("bold", "Bold", "Make the mentioned name bold.", ChatHighlightConfig::bold, ChatHighlightConfig::setBold),
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
	private static int senderEnd(String text) {
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

	/** Returns the same component with your name colored in the message part, or the original when nothing matches. */
	public static Component apply(Component content) {
		if (!on) {
			return content;
		}
		String name = playerName();
		if (name == null || name.isEmpty()) {
			return content;
		}
		String text = content.getString();
		int start = senderEnd(text);
		if (start <= 0 || start >= text.length()) {
			return content;
		}
		Pattern pattern = Pattern.compile("(?<![\\w])" + Pattern.quote(name) + "(?![\\w])", Pattern.CASE_INSENSITIVE);
		if (pattern.matcher(text.substring(0, start)).find()) {
			return content; // your own message
		}
		Matcher matcher = pattern.matcher(text);
		matcher.region(start, text.length());
		boolean[] mark = new boolean[text.length()];
		boolean found = false;
		while (matcher.find()) {
			found = true;
			for (int i = matcher.start(); i < matcher.end(); i++) {
				mark[i] = true;
			}
		}
		if (!found) {
			return content;
		}
		String alertText = text.substring(start);
		if (!alertText.equals(lastAlertText)) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && ChatHighlightConfig.soundVolume() > 0) {
				mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), ChatHighlightConfig.soundVolume(), 1.2F);
			}
			lastAlertText = alertText;
		}

		List<Style> styles = new ArrayList<>(text.length());
		content.visit((style, fragment) -> {
			for (int i = 0; i < fragment.length(); i++) {
				styles.add(style);
			}
			return Optional.empty();
		}, Style.EMPTY);
		if (styles.size() != text.length()) {
			return content;
		}

		ChatFormatting color = ChatHighlightConfig.colorFormat();
		boolean bold = ChatHighlightConfig.bold();
		MutableComponent result = Component.empty();
		int from = 0;
		while (from < text.length()) {
			int end = from + 1;
			while (end < text.length() && mark[end] == mark[from] && styles.get(end).equals(styles.get(from))) {
				end++;
			}
			MutableComponent piece = Component.literal(text.substring(from, end)).withStyle(styles.get(from));
			if (mark[from]) {
				piece = bold ? piece.withStyle(color, ChatFormatting.BOLD) : piece.withStyle(color);
			}
			result.append(piece);
			from = end;
		}
		return result;
	}
}
