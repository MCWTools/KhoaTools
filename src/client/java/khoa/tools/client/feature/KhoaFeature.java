package khoa.tools.client.feature;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import khoa.tools.setting.Setting;

/**
 * One client-side tool inside KhoaTools. Shown as a card in the /khoatools GUI (tab ClientsideTools)
 * and also available as a subcommand of /khoatoolsclient.
 * To add a new tool: implement this interface and register it in {@link KhoaFeatures#all()}.
 * Only {@link #id()} is required; the rest has defaults (the command defaults to "/khoatoolsclient <id>" = activate):
 * <ul>
 * <li>{@link #displayName()} / {@link #description()}: card title and the text shown in its config screen</li>
 * <li>{@link #settings()}: rows of the config screen (BOOL / INT / STRING, see {@link Setting})</li>
 * <li>{@link #activate}: what clicking the card does (toggle, or a one-shot action)</li>
 * <li>{@link #isActive()}: true = the card is drawn green (leave false for one-shot actions)</li>
 * </ul>
 * (Server-side tools go in khoa.tools.command.KhoaServerFeatures and live under /khoatools.)
 */
public interface KhoaFeature {
	/** Short unique id, also used as the config section name. */
	String id();

	/** Name shown on the card. */
	default String displayName() {
		return id();
	}

	/** Shown at the top of the config screen. */
	default String description() {
		return "";
	}

	/** Rows of the config screen. Empty = the config screen only shows the description. */
	default List<Setting> settings() {
		return List.of();
	}

	/** Clicking the card (not the Config button). Runs on the client thread. */
	default void activate(Minecraft mc) {
	}

	/** Green card while true. */
	default boolean isActive() {
		return false;
	}

	/** True = the on/off state of this toggle is saved in config/khoatools.json and restored on the next start. */
	default boolean persistEnabled() {
		return false;
	}

	/** Called once on client start (register events here). */
	default void onInit() {
	}

	/** Add this feature's subcommands to the /khoatoolsclient root. Default: "/khoatoolsclient <id>" calls {@link #activate}. */
	default void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id()).executes(ctx -> {
			activate(ctx.getSource().getClient());
			return 1;
		}));
	}

	/** Chat message to the player (ignored when not in a world). */
	static void say(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(text));
		}
	}
}
