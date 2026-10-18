package khoa.tools.client.chat;

import java.util.List;

import net.minecraft.client.multiplayer.chat.GuiMessage;

/** Implemented on ChatComponent by {@link ChatComponentMixin}. */
public interface ChatComponentExt {
	/** Trim stored messages down to the configured BetterChat length right now. */
	void khoatools$trimToLength();

	/** Add an old (cached) message to the chat history without running the chat pipeline. */
	void khoatools$addRestored(GuiMessage message);
	void khoatools$replace(GuiMessage oldMessage, GuiMessage replacement);

	/** All stored messages, newest first (read only). */
	List<GuiMessage> khoatools$messages();

	/** Wrap every stored message into display lines again (vanilla refreshTrimmedMessages). */
	void khoatools$refresh();

	/** Reset the scroll and scroll so the lines of this message are in the middle of the chat. False when it has no visible lines. */
	boolean khoatools$scrollTo(GuiMessage message);
}
