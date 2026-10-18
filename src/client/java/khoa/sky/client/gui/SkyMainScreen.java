package khoa.sky.client.gui;

import khoa.sky.client.SkyFolders;
import khoa.sky.client.SkyManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** First screen: choose a dimension. */
public class SkyMainScreen extends Screen {
	private final Screen parent;

	public SkyMainScreen(Screen parent) {
		super(Component.literal("SkyChanger"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 4;

		this.addRenderableWidget(Button.builder(Component.literal("Overworld"),
			b -> this.minecraft.setScreen(new SkyDimensionScreen(this, true))).bounds(cx - 100, y, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("The End"),
			b -> this.minecraft.setScreen(new SkyDimensionScreen(this, false))).bounds(cx - 100, y + 26, 200, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Open skyboxes folder"),
			b -> Util.getPlatform().openPath(SkyFolders.root())).bounds(cx - 100, y + 62, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reload images from disk"),
			b -> SkyManager.reloadAll()).bounds(cx - 100, y + 88, 200, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"),
			b -> this.onClose()).bounds(cx - 100, y + 124, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 24, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.literal("Nether has no sky texture, so it is not listed."),
			this.width / 2, this.height / 4 + 152, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
