package khoa.tools.client.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Reverse of {@link RawChatText}: turns typed text with {@code &c}, {@code &#rrggbb}, {@code &l &o &n &m &k}
 * and {@code &r} codes into a styled component. A colour code clears the formats before it, like in vanilla.
 * An {@code &} that is not followed by a valid code stays as plain text.
 */
public final class RawChatParser {
	private RawChatParser() {
	}

	public static Component parse(String raw) {
		MutableComponent root = Component.empty();
		StringBuilder text = new StringBuilder();
		Style style = Style.EMPTY;

		int i = 0;
		while (i < raw.length()) {
			char c = raw.charAt(i);
			if (c == '&' && i + 1 < raw.length()) {
				char code = Character.toLowerCase(raw.charAt(i + 1));
				if (code == '#' && isHex(raw, i + 2, 6)) {
					flush(root, text, style);
					style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(raw.substring(i + 2, i + 8), 16)));
					i += 8;
					continue;
				}
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting != null) {
					flush(root, text, style);
					if (formatting == ChatFormatting.RESET) {
						style = Style.EMPTY;
					} else if (formatting.isColor()) {
						style = Style.EMPTY.withColor(formatting);
					} else {
						style = style.applyFormat(formatting);
					}
					i += 2;
					continue;
				}
			}
			text.append(c);
			i++;
		}
		flush(root, text, style);
		return root;
	}

	private static boolean isHex(String s, int from, int length) {
		if (from + length > s.length()) {
			return false;
		}
		for (int i = from; i < from + length; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return false;
			}
		}
		return true;
	}

	private static void flush(MutableComponent root, StringBuilder text, Style style) {
		if (!text.isEmpty()) {
			root.append(Component.literal(text.toString()).withStyle(style));
			text.setLength(0);
		}
	}
}
