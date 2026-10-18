package khoa.logoutghost.client.ghost;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * /khoatoolsclient logoutghost [status | clear | fade <ticks> | opacity <percent>]
 * (ex-mod "logout-ghost"; its own settings stay in config/logout-ghost.json).
 */
public final class LogoutGhostFeature implements KhoaFeature {
	/** Fade value to come back to when the feature is switched on again (0 means off). */
	private static int lastFade = 100;

	@Override
	public String id() {
		return "logoutghost";
	}

	@Override
	public String displayName() {
		return "Logout Ghost";
	}

	@Override
	public String description() {
		return "Leaves a translucent ghost of a player where they logged out (name, skin, pose), then fades it away. "
			+ "Click the card to turn it on/off. Fade 0 = off, -1 = ghosts never fade.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.integer("fade_ticks", "Fade (ticks)", "How long a ghost takes to fade. 0 = feature off, -1 = ghosts stay forever. 20 ticks = 1s.",
				-1, 1_000_000, () -> GhostConfig.get().fadeTicks, value -> {
					GhostConfig.get().fadeTicks = value;
					GhostConfig.save();
					if (value == 0) {
						GhostManager.clearAll();
					} else {
						lastFade = value;
					}
				}),
			Setting.integer("opacity_percent", "Opacity (%)", "Starting opacity of new ghosts (1-100).",
				1, 100, () -> GhostConfig.get().opacityPercent, value -> {
					GhostConfig.get().opacityPercent = value;
					GhostConfig.save();
				}),
			Setting.bool("show_hitbox", "Show hitbox", "Draw the ghost's hitbox outline (no F3+B needed).",
				() -> GhostConfig.get().showHitbox, value -> {
					GhostConfig.get().showHitbox = value;
					GhostConfig.save();
				}));
	}

	@Override
	public boolean isActive() {
		return GhostConfig.get().fadeTicks != 0;
	}

	@Override
	public void activate(Minecraft mc) {
		GhostConfig c = GhostConfig.get();
		if (c.fadeTicks != 0) {
			lastFade = c.fadeTicks;
			c.fadeTicks = 0;
			GhostManager.clearAll();
		} else {
			c.fadeTicks = lastFade != 0 ? lastFade : 100;
		}
		GhostConfig.save();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Logout Ghost] fade: " + describeFade(c.fadeTicks)));
		}
	}

	@Override
	public void onInit() {
		GhostConfig.load();
		ClientTickEvents.END_CLIENT_TICK.register(GhostManager::tick);
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("logoutghost")
			.executes(LogoutGhostFeature::status)
			.then(ClientCommands.literal("status").executes(LogoutGhostFeature::status))
			.then(ClientCommands.literal("clear").executes(LogoutGhostFeature::clear))
			.then(ClientCommands.literal("fade")
				.then(ClientCommands.argument("ticks", IntegerArgumentType.integer(-1))
					.executes(LogoutGhostFeature::setFade)))
			.then(ClientCommands.literal("opacity")
				.then(ClientCommands.argument("percent", IntegerArgumentType.integer(1, 100))
					.executes(LogoutGhostFeature::setOpacity))));
	}

	private static String describeFade(int ticks) {
		if (ticks == 0) {
			return "disabled (0)";
		}
		if (ticks < 0) {
			return "no fade, ghosts stay (-1)";
		}
		return ticks + " ticks (" + String.format("%.1f", ticks / 20.0) + "s)";
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		GhostConfig c = GhostConfig.get();
		ctx.getSource().sendFeedback(Component.literal(
			"[Logout Ghost] fade: " + describeFade(c.fadeTicks)
				+ " | opacity: " + c.opacityPercent + "% | active ghosts: " + GhostManager.count()));
		return 1;
	}

	private static int clear(CommandContext<FabricClientCommandSource> ctx) {
		int n = GhostManager.clearAll();
		ctx.getSource().sendFeedback(Component.literal("[Logout Ghost] removed " + n + " ghost(s)"));
		return n;
	}

	private static int setFade(CommandContext<FabricClientCommandSource> ctx) {
		int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
		GhostConfig.get().fadeTicks = ticks;
		if (ticks != 0) {
			lastFade = ticks;
		}
		GhostConfig.save();
		if (ticks == 0) {
			GhostManager.clearAll();
		}
		ctx.getSource().sendFeedback(Component.literal("[Logout Ghost] fade: " + describeFade(ticks)));
		return 1;
	}

	private static int setOpacity(CommandContext<FabricClientCommandSource> ctx) {
		int percent = IntegerArgumentType.getInteger(ctx, "percent");
		GhostConfig.get().opacityPercent = percent;
		GhostConfig.save();
		ctx.getSource().sendFeedback(Component.literal("[Logout Ghost] opacity for new ghosts: " + percent + "%"));
		return 1;
	}
}
