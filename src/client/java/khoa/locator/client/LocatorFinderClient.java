package khoa.locator.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import khoa.tools.KhoaTools;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * /khoatoolsclient locatorfinder [player]  (ex-mod "locator-finder"; trace and playerindirection are their own features).
 */
public class LocatorFinderClient implements KhoaFeature {

	/** Names for tab-complete (leading Geyser/Bedrock prefix removed), refreshed every 20 ticks. */
	private static volatile List<String> suggestionNames = List.of();

	private int tickCounter = 0;

	@Override
	public String id() {
		return "locatorfinder";
	}

	@Override
	public String displayName() {
		return "Locator Finder";
	}

	@Override
	public String description() {
		return "Finds a player through the locator bar waypoint and shows an overlay + marker pointing to them. "
			+ "Type the player name in Config, then click the card to start tracking (click again to stop). Bedrock prefixes like '.' are ignored.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.string("player", "Player name", "Name of the player to find (as in the tab list).",
			() -> KhoaConfig.getString(id(), "player", ""), value -> {
				KhoaConfig.put(id(), "player", value);
				if (LocatorTracker.active()) { // already tracking: follow the new name
					PlayerInfo info = findPlayer(Minecraft.getInstance(), value);
					if (info != null) {
						startTracking(info);
					}
				}
			}));
	}

	@Override
	public boolean isActive() {
		return LocatorTracker.active();
	}

	@Override
	public void activate(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (LocatorTracker.active()) {
			LocatorTracker.clear();
			PathPlanner.invalidate();
			mc.player.sendSystemMessage(Component.literal("Locator Finder: tắt"));
			return;
		}
		String name = KhoaConfig.getString(id(), "player", "").trim();
		if (name.isEmpty()) {
			mc.player.sendSystemMessage(Component.literal("Locator Finder: nhập tên player trong Config trước"));
			return;
		}
		PlayerInfo info = findPlayer(mc, name);
		if (info == null) {
			mc.player.sendSystemMessage(Component.literal("Không thấy player '" + name + "' trong tab list"));
			return;
		}
		startTracking(info);
		mc.player.sendSystemMessage(Component.literal("Locator Finder: đang tìm " + info.getProfile().name()));
	}

	private static void startTracking(PlayerInfo info) {
		LocatorTracker.setTarget(info.getProfile().id(), info.getProfile().name());
		PathPlanner.invalidate();
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		// /khoatoolsclient locatorfinder  -> disable, /khoatoolsclient locatorfinder <player>
		root.then(ClientCommands.literal("locatorfinder")
			.executes(ctx -> {
				LocatorTracker.clear();
				PathPlanner.invalidate();
				ctx.getSource().sendFeedback(Component.literal("Locator Finder: tắt"));
				return 1;
			})
			.then(ClientCommands.argument("player", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(suggestionNames, builder))
				.executes(ctx -> {
					String input = StringArgumentType.getString(ctx, "player");
					PlayerInfo info = findPlayer(Minecraft.getInstance(), input);
					if (info == null) {
						ctx.getSource().sendError(Component.literal("Không thấy player '" + input + "' trong tab list"));
						return 0;
					}
					KhoaConfig.put(id(), "player", input);
					startTracking(info);
					ctx.getSource().sendFeedback(Component.literal("Locator Finder: đang tìm " + info.getProfile().name()));
					return 1;
				})));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.getConnection() == null) {
				LocatorTracker.clear();
				PathPlanner.invalidate();
				suggestionNames = List.of();
				tickCounter = 0;
				return;
			}
			PathPlanner.tick(client);
			tickCounter++;
			if (tickCounter % 5 == 0) {
				LocatorTracker.refresh(client);
			}
			if (tickCounter % 20 == 0) {
				suggestionNames = collectNames(client);
			}
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(LocatorWorldMarker::render);
		LevelRenderEvents.BEFORE_GIZMOS.register(LocatorPathRenderer::render);

		HudElementRegistry.addLast(KhoaTools.id("locator_overlay"), LocatorOverlay::render);
	}

	/** Strip any leading non-name chars ('.', '*', ...) that Geyser adds for Bedrock players. */
	static String stripPrefix(String name) {
		int i = 0;
		while (i < name.length() && !isNameChar(name.charAt(i))) i++;
		return name.substring(i);
	}

	private static boolean isNameChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
	}

	private static List<String> collectNames(Minecraft client) {
		List<String> out = new ArrayList<>();
		UUID self = client.player.getUUID();
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			if (info.getProfile().id().equals(self)) continue;
			out.add(stripPrefix(info.getProfile().name()));
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/** Exact name first; otherwise match ignoring the Bedrock prefix, so "Steve" finds ".Steve". */
	private static PlayerInfo findPlayer(Minecraft client, String input) {
		if (client.getConnection() == null) return null;
		String wanted = stripPrefix(input);
		PlayerInfo prefixMatch = null;
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name.equalsIgnoreCase(input)) return info;
			if (prefixMatch == null && stripPrefix(name).equalsIgnoreCase(wanted)) prefixMatch = info;
		}
		return prefixMatch;
	}
}
