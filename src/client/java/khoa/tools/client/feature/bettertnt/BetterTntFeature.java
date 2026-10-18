package khoa.tools.client.feature.bettertnt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Better TNT: client-side cuts for the things a big TNT chain makes the CLIENT do. Off by default (click the card).
 * <ul>
 * <li>Every primed TNT adds one smoke particle per tick (PrimedTnt.tick) and is drawn as a block model.</li>
 * <li>Every explosion packet plays a loud sound, adds an explosion (emitter) particle and up to 512 block particles per tick.</li>
 * <li>The destroyed blocks arrive as block updates and make the chunk meshes rebuild (not touched here).</li>
 * </ul>
 * "/khoatoolsclient bettertnt stats" prints peak numbers since the last call, to see which of them is the real cause.
 */
public class BetterTntFeature implements KhoaFeature {
	private static boolean active = false;

	private static final IntOpenHashSet ALLOWED = new IntOpenHashSet();
	private static int soundsThisTick;

	// stats since the last "stats" call
	private static int peakTnt;
	private static int peakParticles;
	private static int minFps = Integer.MAX_VALUE;
	private static int explosions;
	private static int soundsPlayed;
	private static int soundsSkipped;

	@Override
	public String id() {
		return "bettertnt";
	}

	@Override
	public String displayName() {
		return "Better TNT";
	}

	@Override
	public String description() {
		return "Less client lag from big TNT chains: fewer TNT models, no fuse smoke, fewer explosion particles and sounds. "
			+ "/khoatoolsclient bettertnt stats = peak TNT / particles / explosions and lowest FPS since the last call.";
	}

	@Override
	public boolean persistEnabled() {
		return true;
	}

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public void activate(Minecraft mc) {
		active = !active;
		ALLOWED.clear();
		KhoaFeature.say(mc, "Better TNT: " + (active ? "on" : "off"));
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.integer("tnt_limit", "TNT Models",
				"Only draw this many primed TNT, nearest to the camera first. -1 = no limit, 0 = draw none.",
				-1, 99999, () -> KhoaConfig.getInt(id(), "tnt_limit", -1), v -> KhoaConfig.put(id(), "tnt_limit", v)),
			Setting.bool("hide_fuse_smoke", "Hide Fuse Smoke",
				"Primed TNT no longer makes a smoke particle every tick.",
				() -> KhoaConfig.getBool(id(), "hide_fuse_smoke", false), v -> KhoaConfig.put(id(), "hide_fuse_smoke", v)),
			Setting.bool("hide_explosion_particles", "Hide Explosion Particles",
				"No explosion / explosion emitter particle and no block debris particles when something explodes.",
				() -> KhoaConfig.getBool(id(), "hide_explosion_particles", false), v -> KhoaConfig.put(id(), "hide_explosion_particles", v)),
			Setting.integer("sound_limit", "Explosion Sounds Per Tick",
				"Play at most this many explosion sounds per tick. -1 = no limit, 0 = no explosion sound.",
				-1, 99, () -> KhoaConfig.getInt(id(), "sound_limit", -1), v -> KhoaConfig.put(id(), "sound_limit", v)));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(BetterTntFeature::tick);
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.executes(c -> {
				activate(c.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("stats").executes(c -> {
				printStats(c.getSource().getClient());
				return 1;
			})));
	}

	// ---- per tick ----

	private static void tick(Minecraft mc) {
		soundsThisTick = 0;
		if (mc.level == null) {
			ALLOWED.clear();
			return;
		}
		// stats are always collected (cheap), so "stats" also works with Better TNT off, for comparing
		int tnt = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof PrimedTnt) tnt++;
		}
		peakTnt = Math.max(peakTnt, tnt);
		try {
			peakParticles = Math.max(peakParticles, Integer.parseInt(mc.particleEngine.countParticles()));
		} catch (NumberFormatException ignored) {
		}
		if (tnt > 0 || peakTnt > 0) {
			minFps = Math.min(minFps, mc.getFps());
		}

		ALLOWED.clear();
		int limit = tntLimit();
		if (limit <= 0) return;
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 cam = camera.position();
		List<Entity> list = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof PrimedTnt) list.add(e);
		}
		if (list.size() > limit) {
			list.sort((a, b) -> Double.compare(a.distanceToSqr(cam), b.distanceToSqr(cam)));
		}
		for (int i = 0, n = Math.min(limit, list.size()); i < n; i++) {
			ALLOWED.add(list.get(i).getId());
		}
	}

	private static void printStats(Minecraft mc) {
		KhoaFeature.say(mc, "Better TNT stats since last call: peak primed TNT " + peakTnt
			+ ", peak particles " + peakParticles
			+ ", lowest FPS " + (minFps == Integer.MAX_VALUE ? "-" : String.valueOf(minFps))
			+ ", explosions " + explosions
			+ ", sounds played/skipped " + soundsPlayed + "/" + soundsSkipped);
		peakTnt = 0;
		peakParticles = 0;
		minFps = Integer.MAX_VALUE;
		explosions = 0;
		soundsPlayed = 0;
		soundsSkipped = 0;
	}

	// ---- settings as seen by the mixins ----

	private static int tntLimit() {
		return active ? KhoaConfig.getInt("bettertnt", "tnt_limit", -1) : -1;
	}

	/** EntityRenderDispatcherMixin: true = do not draw this primed TNT. */
	public static boolean isTntHidden(Entity e) {
		if (!(e instanceof PrimedTnt)) return false;
		int limit = tntLimit();
		if (limit < 0) return false;
		return limit == 0 || !ALLOWED.contains(e.getId());
	}

	/** PrimedTntMixin. */
	public static boolean hideFuseSmoke() {
		return active && KhoaConfig.getBool("bettertnt", "hide_fuse_smoke", false);
	}

	/** ClientPacketListenerExplosionMixin. */
	public static boolean active() {
		return active;
	}

	public static boolean hideExplosionParticles() {
		return KhoaConfig.getBool("bettertnt", "hide_explosion_particles", false);
	}

	/** Counts the explosion and tells whether its sound may play this tick. */
	public static boolean explosionSoundAllowed() {
		explosions++;
		int limit = KhoaConfig.getInt("bettertnt", "sound_limit", -1);
		if (limit >= 0 && soundsThisTick >= limit) {
			soundsSkipped++;
			return false;
		}
		soundsThisTick++;
		soundsPlayed++;
		return true;
	}
}
