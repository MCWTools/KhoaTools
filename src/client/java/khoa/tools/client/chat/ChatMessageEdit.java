package khoa.tools.client.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Client-only message edit: the clicked message is greyed out while its raw text sits in the chat box;
 * on send the message is replaced locally (nothing goes to the server), on close it is restored.
 */
public final class ChatMessageEdit {
	private static ChatComponent chat;
	private static GuiMessage original;
	private static GuiMessage greyed;

	private ChatMessageEdit() {
	}

	public static boolean isEditing() {
		return greyed != null;
	}

	/** Grey the message out. Any edit that was already running is cancelled first. */
	public static void begin(ChatComponent component, GuiMessage message) {
		cancel();
		GuiMessage grey = new GuiMessage(message.addedTime(),
			Component.literal(message.content().getString()).withStyle(ChatFormatting.DARK_GRAY),
			null, message.source(), message.tag());
		((ChatComponentExt) component).khoatools$replace(message, grey);
		chat = component;
		original = message;
		greyed = grey;
	}

	/** Replace the greyed message with the typed text. Empty text restores the original. */
	public static void finish(String raw) {
		if (greyed == null) {
			return;
		}
		if (raw.isBlank()) {
			cancel();
			return;
		}
		Component edited = ChatCoordinates.makeClickable(RawChatParser.parse(raw));
		((ChatComponentExt) chat).khoatools$replace(greyed,
			new GuiMessage(original.addedTime(), edited, null, original.source(), original.tag()));
		clear();
	}

	/** Put the original message back. */
	public static void cancel() {
		if (greyed == null) {
			return;
		}
		((ChatComponentExt) chat).khoatools$replace(greyed, original);
		clear();
	}

	private static void clear() {
		chat = null;
		original = null;
		greyed = null;
	}
}
