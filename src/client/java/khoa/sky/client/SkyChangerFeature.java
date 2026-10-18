package khoa.sky.client;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;

import khoa.sky.client.gui.SkyMainScreen;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/** /khoatoolsclient sky - opens the SkyChanger GUI (ex-mod "skychanger"; PNGs in .minecraft/skyboxes). */
public final class SkyChangerFeature implements KhoaFeature {
	/** Set by the command; the screen is opened on the next tick so the chat screen can't close it. */
	private static volatile boolean openGuiNextTick;

	@Override
	public String id() {
		return "sky";
	}

	@Override
	public String displayName() {
		return "Sky Changer";
	}

	@Override
	public String description() {
		return "Change the Overworld / End skybox, sun, moon and clouds from PNG files in .minecraft/skyboxes (no resource pack). "
			+ "Click the card to open the Sky Changer screen.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.action("open_texture_changer", "Texture Changer", "Open the real Sky Changer screen (pick skybox, sun, moon, clouds). Esc comes back here.",
				"Open Texture Changer", () -> {
					Minecraft mc = Minecraft.getInstance();
					mc.setScreen(new SkyMainScreen(mc.screen));
				}),
			Setting.bool("overworld_stars", "Overworld stars", "Draw the vanilla stars in the Overworld night sky.",
				SkyConfig::overworldStars, SkyConfig::setOverworldStars),
			Setting.bool("overworld_sunrise_glow", "Sunrise glow", "Draw the orange glow at sunrise/sunset.",
				SkyConfig::overworldSunriseGlow, SkyConfig::setOverworldSunriseGlow));
	}

	@Override
	public void activate(Minecraft mc) {
		mc.setScreen(new SkyMainScreen(mc.screen)); // Esc/Done comes back to the screen it was opened from
	}

	@Override
	public void onInit() {
		SkyFolders.ensure();
		SkyConfig.load();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openGuiNextTick) {
				openGuiNextTick = false;
				client.setScreen(new SkyMainScreen(null));
			}
		});
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("sky").executes(ctx -> {
			openGuiNextTick = true;
			return 1;
		}));
	}
}
