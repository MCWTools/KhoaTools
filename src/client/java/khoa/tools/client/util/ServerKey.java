package khoa.tools.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Identifies the server (or singleplayer world) the client is connected to. */
public final class ServerKey {
	private ServerKey() {
	}

	/** Human readable name, e.g. "play.example.com" or "singleplayer:My World". */
	public static String display(Minecraft mc) {
		if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
			return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		ServerData data = mc.getCurrentServer();
		if (data != null && data.ip != null && !data.ip.isBlank()) {
			return data.ip.toLowerCase();
		}
		return "unknown";
	}

	/** File-system safe version of {@link #display}. */
	public static String id(Minecraft mc) {
		return display(mc).replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
