package khoa.tools.client.playlist;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Playlist and track editor. */
public final class PlaylistScreen extends Screen {
	private final Screen parent;
	private PlaylistManager.Playlist selected;
	private PlaylistList playlistList;
	private TrackList trackList;
	private EditBox nameBox;

	public PlaylistScreen(Screen parent) { super(Component.literal("Custom Playlists")); this.parent = parent; }

	@Override protected void init() {
		this.clearWidgets();
		int cx = this.width / 2;
		int rightX = cx - 4;
		int rightW = this.width - rightX - 14;
		this.playlistList = new PlaylistList(this.minecraft, Math.min(230, rightX - 24), this.height - 104, 42, 16);
		this.playlistList.setX(14);
		for (PlaylistManager.Playlist playlist : PlaylistManager.playlists()) this.playlistList.addRow(playlist);
		this.addRenderableWidget(this.playlistList);
		int bw = (rightW - 8) / 3;
		this.nameBox = new EditBox(this.font, rightX, 42, rightW, 20, Component.literal("Playlist name"));
		this.nameBox.setMaxLength(48);
		if (selected != null) this.nameBox.setValue(selected.name());
		this.addRenderableWidget(this.nameBox);
		this.addRenderableWidget(Button.builder(Component.literal("Create"), b -> create()).bounds(rightX, 68, bw, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Rename"), b -> { if (selected != null) { PlaylistManager.rename(selected.id(), this.nameBox.getValue()); refresh(); } }).bounds(rightX + bw + 4, 68, bw, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> { if (selected != null) { PlaylistManager.delete(selected.id()); selected = null; refresh(); } }).bounds(rightX + 2 * (bw + 4), 68, bw, 20).build());
		if (selected != null) {
			this.addRenderableWidget(Button.builder(Component.literal((selected.active() ? "Disable" : "Enable") + " playlist"), b -> { PlaylistManager.toggle(selected.id()); refresh(); }).bounds(rightX, 94, bw, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("[" + (selected.shuffle() ? "x" : " ") + "] Shuffle"), b -> { PlaylistManager.setShuffle(selected.id(), !selected.shuffle()); refresh(); }).bounds(rightX + bw + 4, 94, bw, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("[" + (selected.loop() ? "x" : " ") + "] Loop"), b -> { PlaylistManager.setLoop(selected.id(), !selected.loop()); refresh(); }).bounds(rightX + 2 * (bw + 4), 94, bw, 20).build());
			this.trackList = new TrackList(this.minecraft, rightW, this.height - 166, 120, 16);
			this.trackList.setX(rightX);
			for (PlaylistManager.Track track : selected.tracks()) this.trackList.addRow(track);
			this.addRenderableWidget(this.trackList);
			this.addRenderableWidget(Button.builder(Component.literal("Add Song"), b -> this.minecraft.setScreen(new TrackSourceScreen(this, selected))).bounds(rightX, this.height - 40, 112, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Remove selected"), b -> { if (this.trackList.getSelected() != null) { PlaylistManager.removeTrack(selected.id(), selected.tracks().indexOf(this.trackList.getSelected().track)); refresh(); } }).bounds(rightX + 118, this.height - 40, 136, 20).build());
		}
		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(this.width - 94, this.height - 40, 76, 20).build());
	}

	private void create() { selected = PlaylistManager.create(this.nameBox.getValue()); refresh(); }
	private void refresh() { if (selected != null) selected = PlaylistManager.find(selected.id()); rebuildWidgets(); }

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		g.text(this.font, "Playlists", 14, 26, 0xFFAAAAAA);
		if (selected == null) g.text(this.font, "Select a playlist on the left, or enter a name and press Create.", this.width / 2 - 4, 126, 0xFFAAAAAA);
		else g.text(this.font, selected.active() ? "Playing: " + selected.name() : selected.name(), this.width / 2 - 4, 26, selected.active() ? 0xFF55FF77 : 0xFFFFFFFF);
	}

	@Override public void onClose() { this.minecraft.setScreen(parent); }

	private final class PlaylistList extends ObjectSelectionList<PlaylistRow> {
		PlaylistList(Minecraft mc, int w, int h, int y, int rowH) { super(mc, w, h, y, rowH); }
		void addRow(PlaylistManager.Playlist p) { this.addEntry(new PlaylistRow(p)); }
	}
	private final class PlaylistRow extends ObjectSelectionList.Entry<PlaylistRow> {
		final PlaylistManager.Playlist playlist;
		PlaylistRow(PlaylistManager.Playlist p) { playlist = p; }
		@Override public Component getNarration() { return Component.literal(playlist.name()); }
		@Override public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
			g.text(PlaylistScreen.this.font, (playlist.active() ? "> " : "") + playlist.name(), getContentX() + 4, getContentYMiddle() - 4, playlist.active() ? 0xFF55FF77 : 0xFFFFFFFF);
		}
		@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			selected = PlaylistManager.find(playlist.id()); rebuildWidgets(); return true;
		}
	}
	private final class TrackList extends ObjectSelectionList<TrackRow> {
		TrackList(Minecraft mc, int w, int h, int y, int rowH) { super(mc, w, h, y, rowH); }
		void addRow(PlaylistManager.Track t) { this.addEntry(new TrackRow(t)); }
	}
	private final class TrackRow extends ObjectSelectionList.Entry<TrackRow> {
		final PlaylistManager.Track track;
		TrackRow(PlaylistManager.Track t) { track = t; }
		@Override public Component getNarration() { return Component.literal(track.title()); }
		@Override public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
			g.text(PlaylistScreen.this.font, track.title() + (track.source() == PlaylistManager.Source.DEVICE ? " [device]" : ""), getContentX() + 4, getContentYMiddle() - 4, 0xFFFFFFFF);
		}
		@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { trackList.setSelected(this); return true; }
	}

	private static final class TrackSourceScreen extends Screen {
		private final PlaylistScreen parent;
		private final PlaylistManager.Playlist playlist;
		private SourceTrackList tracks;
		private boolean deviceSource;
		private TrackSourceScreen(PlaylistScreen parent, PlaylistManager.Playlist playlist) { super(Component.literal("Add Song")); this.parent = parent; this.playlist = playlist; }
		@Override protected void init() {
			this.clearWidgets(); int cx = this.width / 2;
			this.addRenderableWidget(Button.builder(Component.literal((deviceSource ? "  " : "x ") + "From Minecraft"), b -> { deviceSource = false; rebuildWidgets(); }).bounds(cx - 160, 36, 154, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal((deviceSource ? "x " : "  ") + "From Device"), b -> { deviceSource = true; rebuildWidgets(); }).bounds(cx + 6, 36, 154, 20).build());
			List<PlaylistManager.Track> available = deviceSource ? PlaylistManager.availableDeviceTracks() : PlaylistManager.availableMinecraftTracks();
			int listW = Math.min(440, this.width - 32);
			this.tracks = new SourceTrackList(this.minecraft, listW, this.height - 112, 64, 16);
			this.tracks.setX(cx - listW / 2);
			for (PlaylistManager.Track t : available) this.tracks.addRow(t);
			this.addRenderableWidget(this.tracks);
			this.addRenderableWidget(Button.builder(Component.literal("Add selected"), b -> { if (tracks.getSelected() != null) PlaylistManager.addTrack(playlist.id(), tracks.getSelected().track); this.minecraft.setScreen(parent); parent.refresh(); }).bounds(cx - 154, this.height - 40, 140, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.setScreen(parent)).bounds(cx + 14, this.height - 40, 140, 20).build());
		}
		@Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			super.extractRenderState(g, mouseX, mouseY, partialTick); g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
			g.centeredText(this.font, "Choose a Minecraft soundtrack or a file from custommusic.", this.width / 2, 26, 0xFFAAAAAA);
			if (tracks != null && tracks.children().isEmpty()) {
				String message = deviceSource ? "No OGG or MP3 files found in .minecraft/khoatools/custommusic"
					: (this.minecraft.level == null ? "Enter a world to browse Minecraft music." : "No Minecraft soundtrack entries are available.");
				g.centeredText(this.font, message, this.width / 2, 94, 0xFFAAAAAA);
			}
			if (deviceSource) g.centeredText(this.font, "External audio playback needs a decoder library.", this.width / 2, this.height - 60, 0xFFFFAA00);
		}
		private final class SourceTrackList extends ObjectSelectionList<SourceTrackRow> {
			SourceTrackList(Minecraft mc, int w, int h, int y, int rowH) { super(mc, w, h, y, rowH); }
			void addRow(PlaylistManager.Track track) { this.addEntry(new SourceTrackRow(track)); }
		}
		private final class SourceTrackRow extends ObjectSelectionList.Entry<SourceTrackRow> {
			final PlaylistManager.Track track;
			SourceTrackRow(PlaylistManager.Track track) { this.track = track; }
			@Override public Component getNarration() { return Component.literal(track.title()); }
			@Override public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
				g.text(TrackSourceScreen.this.font, track.title(), getContentX() + 4, getContentYMiddle() - 4, 0xFFFFFFFF);
			}
			@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { tracks.setSelected(this); return true; }
		}
	}
}
