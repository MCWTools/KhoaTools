package khoa.tools.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Colors the marked characters of a chat component and keeps every other style (click, hover, colors) as it was. */
public final class ChatMarker {
	private ChatMarker() {
	}

	/** {@code mark[i]} = character i of {@code text} (= content.getString()) gets the color. Returns the input when styles can't be mapped. */
	public static Component paint(Component content, String text, boolean[] mark, ChatFormatting color, boolean bold, boolean underline) {
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

		List<ChatFormatting> formats = new ArrayList<>(3);
		formats.add(color);
		if (bold) {
			formats.add(ChatFormatting.BOLD);
		}
		if (underline) {
			formats.add(ChatFormatting.UNDERLINE);
		}
		ChatFormatting[] extra = formats.toArray(new ChatFormatting[0]);

		MutableComponent result = Component.empty();
		int from = 0;
		while (from < text.length()) {
			int end = from + 1;
			while (end < text.length() && mark[end] == mark[from] && styles.get(end).equals(styles.get(from))) {
				end++;
			}
			MutableComponent piece = Component.literal(text.substring(from, end)).withStyle(styles.get(from));
			result.append(mark[from] ? piece.withStyle(extra) : piece);
			from = end;
		}
		return result;
	}
}
