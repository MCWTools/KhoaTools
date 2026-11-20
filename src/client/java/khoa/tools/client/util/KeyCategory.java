package khoa.tools.client.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** The single "KhoaTools" category of the Controls screen (registering it twice throws, so every feature shares this). */
public final class KeyCategory {
	private static KeyMapping.Category category;

	private KeyCategory() {
	}

	public static synchronized KeyMapping.Category get() {
		if (category == null) {
			category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("khoatools", "main"));
		}
		return category;
	}
}
