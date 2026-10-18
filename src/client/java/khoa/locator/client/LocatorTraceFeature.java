package khoa.locator.client;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/** /khoatoolsclient locatortrace [repeatdelay] - draws a path to the tracked player (needs Locator Finder on). */
public final class LocatorTraceFeature implements KhoaFeature {
	@Override
	public String id() {
		return "locatortrace";
	}

	@Override
	public String displayName() {
		return "Locator Path Trace";
	}

	@Override
	public String description() {
		return "Draws a walking path to the player tracked by Locator Finder. Click the card to turn it on/off. "
			+ "Repeat delay = how often the path is recalculated (0 = calculate once).";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.integer("repeatdelay", "Repeat delay (ticks)", "Ticks between path recalculations. 0 = only once.",
			0, 24000, PathPlanner::repeatDelay, value -> {
				PathPlanner.setRepeatDelay(value);
				KhoaConfig.put(id(), "repeatdelay", value);
			}));
	}

	@Override
	public boolean isActive() {
		return PathPlanner.enabled();
	}

	@Override
	public void activate(Minecraft mc) {
		boolean on = !PathPlanner.enabled();
		PathPlanner.setEnabled(on);
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("Locator Trace: " + (on ? "bật (repeatdelay " + PathPlanner.repeatDelay() + " tick)" : "tắt")));
		}
	}

	@Override
	public void onInit() {
		int saved = KhoaConfig.getInt(id(), "repeatdelay", -1);
		if (saved >= 0) {
			PathPlanner.setRepeatDelay(saved);
		}
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("locatortrace")
			.executes(ctx -> {
				activate(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.argument("repeatdelay", IntegerArgumentType.integer(0, 24000))
				.executes(ctx -> {
					int ticks = IntegerArgumentType.getInteger(ctx, "repeatdelay");
					PathPlanner.setRepeatDelay(ticks);
					KhoaConfig.put(id(), "repeatdelay", ticks);
					PathPlanner.setEnabled(true);
					ctx.getSource().sendFeedback(Component.literal("Locator Trace: bật, repeatdelay = " + ticks + (ticks == 0 ? " (không tính lại)" : " tick")));
					return 1;
				})));
	}
}
