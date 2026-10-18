package khoa.tools.client.feature.playlist;

import java.util.List;

import net.minecraft.client.Minecraft;

import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.gui.KhoaToolsScreen;
import khoa.tools.client.playlist.PlaylistScreen;

/** Opens the custom playlist manager as a normal KhoaTools client feature. */
public final class PlaylistFeature implements KhoaFeature {
	@Override public String id() { return "playlist"; }
	@Override public String displayName() { return "Playlist"; }
	@Override public String description() { return "Create and manage custom music playlists."; }
	@Override public List<khoa.tools.setting.Setting> settings() { return List.of(); }
	@Override public void activate(Minecraft mc) { mc.setScreen(new PlaylistScreen(mc.screen instanceof KhoaToolsScreen ? mc.screen : null)); }
}
