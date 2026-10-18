package khoa.sky.client.gui;

import khoa.sky.client.SkyConfig;
import khoa.sky.client.SkyFolders;
import khoa.sky.client.SkyManager;
import khoa.sky.client.SkySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Objects;

/** Scrollable list of the PNGs in the slot's folder. Clicking one applies it immediately. */
public class SkyPickScreen extends Screen {
	private final Screen parent;
	private final SkySlot slot;
	private FileList list;
	private boolean folderEmpty;

	public SkyPickScreen(Screen parent, SkySlot slot) {
		super(Component.literal((slot.dimensionDir.equals("overworld") ? "Overworld" : "The End") + " - " + slot.label));
		this.parent = parent;
		this.slot = slot;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		this.list = new FileList(this.minecraft, this.width, this.height - 32 - 56, 32, 22);

		String current = SkyConfig.get(this.slot);
		Row selected = null;
		Row defaultRow = new Row(null);
		this.list.addRow(defaultRow);
		if (current == null) {
			selected = defaultRow;
		}
		List<String> files = SkyFolders.list(this.slot);
		this.folderEmpty = files.isEmpty();
		for (String file : files) {
			Row row = new Row(file);
			this.list.addRow(row);
			if (file.equals(current)) {
				selected = row;
			}
		}
		if (selected != null) {
			this.list.setSelected(selected);
		}
		this.addRenderableWidget(this.list);

		int by = this.height - 48;
		this.addRenderableWidget(Button.builder(Component.literal("Open folder"),
			b -> Util.getPlatform().openPath(SkyFolders.dir(this.slot))).bounds(cx - 154, by, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Refresh"),
			b -> {
				SkyManager.reloadAll();
				this.rebuildWidgets();
			}).bounds(cx - 50, by, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"),
			b -> this.onClose()).bounds(cx + 54, by, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		if (this.folderEmpty) {
			graphics.centeredText(this.font, Component.literal("No .png files yet - press \"Open folder\" and add some."),
				this.width / 2, 32 + 22 + 8, 0xFFFFAA00);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private final class FileList extends ObjectSelectionList<Row> {
		FileList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
		}

		void addRow(Row row) {
			this.addEntry(row);
		}
	}

	private final class Row extends ObjectSelectionList.Entry<Row> {
		/** png file name, or null for "Default". */
		private final String name;

		Row(String name) {
			this.name = name;
		}

		private String label() {
			return this.name == null ? "Default (vanilla)" : this.name;
		}

		@Override
		public Component getNarration() {
			return Component.literal(this.label());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			boolean active = Objects.equals(SkyConfig.get(SkyPickScreen.this.slot), this.name);
			int color = active ? 0xFFFFD84D : 0xFFFFFFFF;
			graphics.text(SkyPickScreen.this.font, this.label(), this.getContentX() + 4, this.getContentYMiddle() - 4, color);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			SkyConfig.set(SkyPickScreen.this.slot, this.name);
			SkyManager.onSelectionChanged(SkyPickScreen.this.slot);
			SkyPickScreen.this.list.setSelected(this);
			return true;
		}
	}
}
