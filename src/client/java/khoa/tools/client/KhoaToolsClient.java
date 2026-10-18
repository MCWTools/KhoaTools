package khoa.tools.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;

import khoa.tools.KhoaTools;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.config.KhoaKeybinds;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.KhoaFeatures;
import khoa.tools.client.gui.KhoaToolsScreen;
import khoa.tools.client.playlist.PlaylistManager;

public class KhoaToolsClient implements ClientModInitializer {
	/** Set by the commands; the screen is opened on the next tick so the chat screen can't close it again. */
	private static volatile boolean openGuiNextTick;

	@Override
	public void onInitializeClient() {
		KhoaConfig.load();

		for (KhoaFeature feature : KhoaFeatures.all()) {
			try {
				feature.onInit();
			} catch (Exception e) {
				KhoaTools.LOGGER.error("Feature {} failed to init", feature.id(), e);
			}
		}

		// Toggle features that opt in (persistEnabled): restore the saved on/off state, then save every change.
		java.util.Map<KhoaFeature, Boolean> lastEnabled = new java.util.HashMap<>();
		for (KhoaFeature feature : KhoaFeatures.all()) {
			if (!feature.persistEnabled()) {
				continue;
			}
			try {
				if (KhoaConfig.getBool(feature.id(), "enabled", false) && !feature.isActive()) {
					feature.activate(Minecraft.getInstance());
				}
			} catch (Exception e) {
				KhoaTools.LOGGER.error("Feature {} failed to restore its state", feature.id(), e);
			}
			lastEnabled.put(feature, feature.isActive());
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (java.util.Map.Entry<KhoaFeature, Boolean> e : lastEnabled.entrySet()) {
				boolean now = e.getKey().isActive();
				if (now != e.getValue()) {
					e.setValue(now);
					KhoaConfig.put(e.getKey().id(), "enabled", now);
				}
			}
			KhoaKeybinds.tick(client);
			PlaylistManager.tick();
			if (openGuiNextTick) {
				openGuiNextTick = false;
				client.setScreen(new KhoaToolsScreen());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			// /khoatoolsclient = same GUI, plus one subcommand per feature (the old way, still works).
			LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("khoatoolsclient")
				.executes(ctx -> {
					openGuiNextTick = true;
					return 1;
				});
			for (KhoaFeature feature : KhoaFeatures.all()) {
				feature.registerCommands(root, context);
			}
			dispatcher.register(root);

			// /khoagui = opens the GUI, no arguments. (/khoatools is the server-side, op-only command and has no GUI.)
			dispatcher.register(ClientCommands.literal("khoagui")
				.executes(ctx -> {
					openGuiNextTick = true;
					return 1;
				}));
		});
	}
}
