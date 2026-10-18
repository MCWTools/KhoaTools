package khoa.tools;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import khoa.tools.command.KhoaServerFeature;
import khoa.tools.command.KhoaServerFeatures;

public class KhoaTools implements ModInitializer {
	public static final String MOD_ID = "khoatools";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// /khoatools = server-side commands. Client-side ones are under /khoatoolsclient.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("khoatools")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					StringBuilder ids = new StringBuilder();
					for (KhoaServerFeature feature : KhoaServerFeatures.all()) {
						ids.append(ids.isEmpty() ? "" : ", ").append(feature.id());
					}
					String text = ids.isEmpty() ? "none yet" : ids.toString();
					ctx.getSource().sendSuccess(() -> Component.literal("KhoaTools server features: " + text), false);
					return 1;
				});
			for (KhoaServerFeature feature : KhoaServerFeatures.all()) {
				feature.registerCommands(root, registryAccess);
			}
			dispatcher.register(root);
		});
		LOGGER.info("KhoaTools loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
