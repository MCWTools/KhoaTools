package khoa.el.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** /khoatoolsclient entitylimiter <num>  (ex-mod "entitylimiter"). */
public class EntityLimiterClient implements KhoaFeature {
	/** -1 = off, 0 = hide all except players, N = show N nearest non-player entities. */
	public static int limit = -1;
	private static final IntOpenHashSet ALLOWED = new IntOpenHashSet();

	@Override
	public String id() {
		return "entitylimiter";
	}

	@Override
	public String displayName() {
		return "Entity Limiter";
	}

	@Override
	public String description() {
		return "Limits how many non-player entities are rendered (the nearest ones stay, players are always shown). "
			+ "Click the card to turn it on/off. 0 hides every non-player entity.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.integer("max_entities", "Max entities shown", "How many nearest entities are rendered while the limiter is on.",
			0, 100000, () -> KhoaConfig.getInt(id(), "max_entities", 30), value -> {
				KhoaConfig.put(id(), "max_entities", value);
				if (limit >= 0) {
					limit = value;
					recompute(Minecraft.getInstance());
				}
			}));
	}

	@Override
	public boolean isActive() {
		return limit >= 0;
	}

	@Override
	public void activate(Minecraft mc) {
		limit = limit >= 0 ? -1 : KhoaConfig.getInt(id(), "max_entities", 30);
		recompute(mc);
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(limit < 0 ? "EntityLimiter: off" : "EntityLimiter: max " + limit + " entities (players always shown)"));
		}
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(EntityLimiterClient::recompute);
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("entitylimiter")
			.executes(c -> {
				c.getSource().sendFeedback(Component.literal("EntityLimiter: " + (limit < 0 ? "off" : limit)));
				return 1;
			})
			.then(ClientCommands.argument("num", IntegerArgumentType.integer(-1))
				.executes(c -> {
					limit = IntegerArgumentType.getInteger(c, "num");
					if (limit >= 0) {
						KhoaConfig.put(id(), "max_entities", limit);
					}
					recompute(Minecraft.getInstance());
					c.getSource().sendFeedback(Component.literal(
						limit < 0 ? "EntityLimiter: off" : "EntityLimiter: max " + limit + " entities (players always shown)"));
					return 1;
				})));
	}

	private static void recompute(Minecraft mc) {
		ALLOWED.clear();
		LocalPlayer self = mc.player;
		if (limit <= 0 || mc.level == null || self == null) return;

		List<Entity> list = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Player)) list.add(e);
		}
		if (list.size() > limit) {
			list.sort((a, b) -> Double.compare(a.distanceToSqr(self), b.distanceToSqr(self)));
		}
		for (int i = 0, n = Math.min(limit, list.size()); i < n; i++) {
			ALLOWED.add(list.get(i).getId());
		}
	}

	public static boolean isHidden(Entity e) {
		if (limit < 0 || e instanceof Player) return false;
		LocalPlayer self = Minecraft.getInstance().player;
		if (self != null && (e == self.getVehicle())) return false;
		return limit == 0 || !ALLOWED.contains(e.getId());
	}
}
