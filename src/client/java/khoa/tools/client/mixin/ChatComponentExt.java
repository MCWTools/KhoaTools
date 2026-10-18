package khoa.tools.client.mixin;

import net.minecraft.client.multiplayer.chat.GuiMessage;

/** Implemented on ChatComponent by {@link ChatComponentMixin}. */
public interface ChatComponentExt {
	/** Trim stored messages down to the configured BetterChat length right now. */
	void khoatools$trimToLength();

	/** Add an old (cached) message to the chat history without running the chat pipeline. */
	void khoatools$addRestored(GuiMessage message);
}
