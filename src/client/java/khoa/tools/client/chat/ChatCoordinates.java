package khoa.tools.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Adds a clipboard click action to coordinate spans while preserving the message's styling. */
public final class ChatCoordinates {
	private static final Pattern COORDINATES = Pattern.compile("(?<![\\w.+-])-?\\d+(?:\\s+-?\\d+){1,2}(?![\\w.+-])");

	private ChatCoordinates() {
	}

	public static Component makeClickable(Component message) {
		String text = message.getString();
		if (text.isEmpty()) {
			return message;
		}

		List<Style> styles = new ArrayList<>(text.length());
		message.visit((style, fragment) -> {
			for (int i = 0; i < fragment.length(); i++) {
				styles.add(style);
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		if (styles.size() != text.length()) {
			return message;
		}

		boolean[] clickable = new boolean[text.length()];
		String[] copies = new String[text.length()];
		Matcher matcher = COORDINATES.matcher(text);
		while (matcher.find()) {
			boolean hasClickAction = false;
			for (int i = matcher.start(); i < matcher.end(); i++) {
				hasClickAction |= styles.get(i).getClickEvent() != null;
			}
			if (hasClickAction) {
				continue;
			}
			String coordinate = matcher.group().trim().replaceAll("\\s+", " ");
			for (int i = matcher.start(); i < matcher.end(); i++) {
				clickable[i] = true;
				copies[i] = coordinate;
			}
		}

		MutableComponent result = Component.empty();
		int start = 0;
		while (start < text.length()) {
			Style style = styles.get(start);
			if (clickable[start]) {
				style = style.withClickEvent(new ClickEvent.CopyToClipboard(copies[start]));
			}
			int end = start + 1;
			while (end < text.length() && styles.get(end).equals(styles.get(start))
				&& clickable[end] == clickable[start]
				&& (!clickable[start] || copies[end].equals(copies[start]))) {
				end++;
			}
			result.append(Component.literal(text.substring(start, end)).withStyle(style));
			start = end;
		}
		return result;
	}

	public static boolean isCoordinate(String value) {
		return COORDINATES.matcher(value).matches();
	}
}
