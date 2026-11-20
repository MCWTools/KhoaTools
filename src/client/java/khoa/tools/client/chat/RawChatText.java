package khoa.tools.client.chat;

import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Turns a chat component into text that keeps every colour and format when it is typed back into chat:
 * the 16 vanilla colours as {@code &c}, any other (hex / gradient) colour as {@code &#rrggbb}, formats as
 * {@code &l &o &n &m &k}, and {@code &r} where a colour or format has to be removed.
 * Whether the pasted text is coloured is up to the server (chat plugins that translate {@code &} codes).
 */
public final class RawChatText {
	private RawChatText() {
	}

	public static String toRaw(Component component) {
		StringBuilder out = new StringBuilder();
		Style[] previous = {Style.EMPTY};

		component.visit((style, text) -> {
			if (text.isEmpty()) {
				return Optional.empty();
			}
			if (!sameLook(previous[0], style)) {
				appendStyle(out, previous[0], style);
				previous[0] = style;
			}
			out.append(text);
			return Optional.empty();
		}, Style.EMPTY);

		return out.toString();
	}

	private static boolean sameLook(Style a, Style b) {
		return colorOf(a) == colorOf(b) && a.isBold() == b.isBold() && a.isItalic() == b.isItalic()
			&& a.isUnderlined() == b.isUnderlined() && a.isStrikethrough() == b.isStrikethrough()
			&& a.isObfuscated() == b.isObfuscated();
	}

	/** RGB of the style colour, or -1 for none. */
	private static int colorOf(Style style) {
		TextColor color = style.getColor();
		return color == null ? -1 : color.getValue();
	}

	private static boolean hasFormat(Style style) {
		return style.isBold() || style.isItalic() || style.isUnderlined() || style.isStrikethrough() || style.isObfuscated();
	}

	private static void appendStyle(StringBuilder out, Style previous, Style style) {
		int color = colorOf(style);
		if (color < 0 && (colorOf(previous) >= 0 || hasFormat(previous))) {
			out.append("&r");
		}
		if (color >= 0) {
			// a colour code also clears the formats before it, so the formats are written again after it
			out.append(colorCode(color));
		}
		if (style.isBold()) {
			out.append("&l");
		}
		if (style.isItalic()) {
			out.append("&o");
		}
		if (style.isUnderlined()) {
			out.append("&n");
		}
		if (style.isStrikethrough()) {
			out.append("&m");
		}
		if (style.isObfuscated()) {
			out.append("&k");
		}
	}

	private static String colorCode(int rgb) {
		for (ChatFormatting formatting : ChatFormatting.values()) {
			if (formatting.isColor() && formatting.getColor() != null && formatting.getColor() == rgb) {
				return "&" + formatting.getChar();
			}
		}
		return String.format("&#%06x", rgb & 0xFFFFFF);
	}
}
