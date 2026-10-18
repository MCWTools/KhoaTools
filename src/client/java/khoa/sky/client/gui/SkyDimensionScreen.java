package khoa.sky.client.gui;

import khoa.sky.client.SkyConfig;
import khoa.sky.client.SkySlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-dimension screen: one row per thing you can change (Skybox / Sun / Moon / Clouds). */
public class SkyDimensionScreen extends Screen {
	private final Screen parent;
	private final boolean overworld;

	public SkyDimensionScreen(Screen parent, boolean overworld) {
		super(Component.literal(overworld ? "Overworld" : "The End"));
		this.parent = parent;
		this.overworld = overworld;
	}

	private SkySlot[] slots() {
		return this.overworld
			? new SkySlot[]{SkySlot.OVERWORLD_SKYBOX, SkySlot.OVERWORLD_SUN, SkySlot.OVERWORLD_MOON, SkySlot.OVERWORLD_CLOUDS}
			: new SkySlot[]{SkySlot.END_SKYBOX};
	}

	private static String shown(SkySlot slot) {
		String name = SkyConfig.get(slot);
		return name == null ? "Default" : name;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 6 + 8;

		for (SkySlot slot : this.slots()) {
			this.addRenderableWidget(Button.builder(Component.literal(slot.label + ": " + shown(slot)),
				b -> this.minecraft.setScreen(new SkyPickScreen(this, slot))).bounds(cx - 120, y, 240, 20).build());
			y += 26;
		}

		if (this.overworld) {
			y += 8;
			this.addRenderableWidget(Button.builder(starsLabel(), b -> {
				SkyConfig.setOverworldStars(!SkyConfig.overworldStars());
				b.setMessage(starsLabel());
			}).bounds(cx - 120, y, 118, 20).build());
			this.addRenderableWidget(Button.builder(glowLabel(), b -> {
				SkyConfig.setOverworldSunriseGlow(!SkyConfig.overworldSunriseGlow());
				b.setMessage(glowLabel());
			}).bounds(cx + 2, y, 118, 20).build());
			y += 26;
		}

		y += 10;
		this.addRenderableWidget(Button.builder(Component.literal("Back"),
			b -> this.onClose()).bounds(cx - 100, y, 200, 20).build());
	}

	private static Component starsLabel() {
		return Component.literal("Stars: " + (SkyConfig.overworldStars() ? "On" : "Off"));
	}

	private static Component glowLabel() {
		return Component.literal("Sunrise glow: " + (SkyConfig.overworldSunriseGlow() ? "On" : "Off"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 6 - 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
