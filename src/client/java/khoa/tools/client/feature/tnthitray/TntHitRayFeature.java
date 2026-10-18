package khoa.tools.client.feature.tnthitray;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.command.OptimizeTntFeature;
import khoa.tools.setting.Setting;

/**
 * Show TNT Hit Ray: replays the vanilla explosion algorithm (ServerExplosion) on the client for every primed TNT.
 * <ul>
 * <li>If an entity is inside the blast range: for each such entity the exact getSeenPercent sample rays are cast
 * (same grid, same ClipContext) and only the rays that reach the explosion centre (the "hit" rays) are drawn.</li>
 * <li>Otherwise: the 1352 block rays of calculateExplodedPositions are walked with the same step / resistance maths.
 * The strength of each ray is random on the server (power * (0.7..1.3)), so each ray is drawn in two parts:
 * orange = reached even with the weakest roll (0.7), yellow = reached only with a stronger roll (up to 1.3).</li>
 * </ul>
 * The client only knows the entities and blocks it has loaded, and TNT power is not synced (setting, default 4).
 */
public final class TntHitRayFeature implements KhoaFeature {
	private static final String ID = "tnthitray";
	private static final int HIT = ARGB.color(255, 80, 255, 120);
	private static final int BLOCKED = ARGB.color(200, 255, 60, 60);
	private static final int SURE = ARGB.color(255, 255, 150, 30);
	private static final int MAYBE = ARGB.color(170, 255, 230, 80);
	private static final int MAX_ENTITIES_PER_TNT = 64;
	private static final double TNT_RANGE_SQR = 128.0 * 128.0;

	private record Line(Vec3 a, Vec3 b, int color, float width) {
	}

	/** Rays stay on screen this many ticks. */
	private static final long LIFETIME_TICKS = 5;
	/** TNT closer than this (blocks) to an already handled TNT gets no rays (same spot, would just overdraw). */
	private static final double MIN_TNT_GAP_SQR = 0.05 * 0.05;

	private record Batch(long born, List<Line> lines) {
	}

	/** Collects lines and counts rays (one block ray can be two lines) against Max Rays. */
	private static final class Sink {
		final List<Line> lines = new ArrayList<>();
		final int max;
		int rays;

		Sink(int max) {
			this.max = max;
		}

		boolean full() {
			return rays >= max;
		}
	}

	private static boolean on;
	private static volatile List<Batch> batches = List.of();

	private static int maxRays() {
		return Math.max(10, KhoaConfig.getInt(ID, "max_rays", 5000));
	}

	private static int power() {
		return Math.max(1, Math.min(100, KhoaConfig.getInt(ID, "power", 4)));
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Show TNT Hit Ray";
	}

	@Override
	public String description() {
		return "Shows the real explosion rays of every primed TNT (vanilla algorithm, client-side blocks). "
			+ "If an entity is in blast range only the rays that actually hit it are drawn (green). "
			+ "Otherwise the block rays are drawn up to where blocks stop them (orange = always reached, yellow = only with a strong roll). "
			+ "TNT power is not synced to the client, set it below if it is not 4.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.integer("power", "TNT power", "Explosion power of the TNT (vanilla TNT = 4).", 1, 100,
				TntHitRayFeature::power, v -> KhoaConfig.put(ID, "power", v)),
			Setting.slider("max_tnt", "Max TNT", "How many TNT (nearest first) get rays.", 1, 10,
				() -> KhoaConfig.getInt(ID, "max_tnt", 3), v -> KhoaConfig.put(ID, "max_tnt", v)),
			Setting.integer("max_rays", "Max Rays", "Most rays drawn per tick (10 or more). Raise it for a full picture, lower it if FPS drops.", 10, 999_999_999,
				TntHitRayFeature::maxRays, v -> KhoaConfig.put(ID, "max_rays", v)),
			Setting.bool("show_blocked", "Show blocked entity rays", "Also draw the entity rays that are stopped by a block (red, ends at the block).",
				() -> KhoaConfig.getBool(ID, "show_blocked", false), v -> KhoaConfig.put(ID, "show_blocked", v)),
			Setting.bool("hide_player_rays", "Hide rays for players", "Do not draw the rays that go to players (other entities still get theirs).",
				() -> KhoaConfig.getBool(ID, "hide_player_rays", false), v -> KhoaConfig.put(ID, "hide_player_rays", v)),
			Setting.bool("show_all_rays", "Show every ray", "Always draw all the block rays (orange / yellow), also when an entity is in blast range, on top of the entity rays.",
				() -> KhoaConfig.getBool(ID, "show_all_rays", false), v -> KhoaConfig.put(ID, "show_all_rays", v)),
			Setting.slider("ray_percent", "Ray multiplier %", "Block rays are drawn like the server casts them with /khoatools optimizetnt <raymultiplier> (100 = all rays). In singleplayer, with optimizetnt on, the real multiplier is used automatically.",
				5, 100, () -> KhoaConfig.getInt(ID, "ray_percent", 100), v -> KhoaConfig.put(ID, "ray_percent", v)));
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		batches = List.of();
		KhoaFeature.say(mc, "Show TNT Hit Ray: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(TntHitRayFeature::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> batches = List.of());
		LevelRenderEvents.END_EXTRACTION.register(context -> draw());
	}

	private static void draw() {
		if (!on) {
			return;
		}
		for (Batch b : batches) {
			for (Line l : b.lines) {
				Gizmos.line(l.a, l.b, l.color, l.width).setAlwaysOnTop();
			}
		}
	}

	private static void tick(Minecraft mc) {
		if (!on) {
			return;
		}
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			batches = List.of();
			return;
		}
		long now = level.getGameTime();
		List<Batch> alive = new ArrayList<>();
		for (Batch b : batches) {
			if (now - b.born < LIFETIME_TICKS) {
				alive.add(b);
			}
		}
		List<PrimedTnt> tnts = new ArrayList<>();
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof PrimedTnt t && t.distanceToSqr(mc.player) <= TNT_RANGE_SQR) {
				tnts.add(t);
			}
		}
		tnts.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
		int max = Math.max(1, Math.min(10, KhoaConfig.getInt(ID, "max_tnt", 3)));
		boolean showBlocked = KhoaConfig.getBool(ID, "show_blocked", false);
		float radius = power();
		boolean hidePlayers = KhoaConfig.getBool(ID, "hide_player_rays", false);
		boolean showAll = KhoaConfig.getBool(ID, "show_all_rays", false);
		double multiplier = rayMultiplier(mc);

		Sink out = new Sink(maxRays());
		List<Vec3> done = new ArrayList<>();
		for (int i = 0, n = Math.min(max, tnts.size()); i < n && !out.full(); i++) {
			PrimedTnt tnt = tnts.get(i);
			Vec3 center = new Vec3(tnt.getX(), tnt.getY(0.0625), tnt.getZ()); // same point PrimedTnt.explode() uses
			boolean tooClose = false;
			for (Vec3 d : done) {
				if (d.distanceToSqr(center) < MIN_TNT_GAP_SQR) {
					tooClose = true;
					break;
				}
			}
			if (tooClose) {
				continue;
			}
			done.add(center);
			List<Entity> inRange = entitiesInRange(level, tnt, center, radius);
			if (inRange.isEmpty() || showAll) {
				blockRays(level, center, radius, multiplier, out);
			}
			for (Entity e : inRange) {
				if (out.full()) {
					break;
				}
				if (hidePlayers && e instanceof Player) {
					continue;
				}
				entityRays(level, e, center, showBlocked, out);
			}
		}
		if (!out.lines.isEmpty()) {
			alive.add(new Batch(now, out.lines));
		}
		batches = alive;
	}

	// ---- entities: ServerExplosion.hurtEntities + getSeenPercent ----

	private static List<Entity> entitiesInRange(ClientLevel level, PrimedTnt tnt, Vec3 center, float radius) {
		float diameter = radius * 2.0F;
		AABB box = new AABB(
			Mth.floor(center.x - (double) diameter - 1.0), Mth.floor(center.y - (double) diameter - 1.0), Mth.floor(center.z - (double) diameter - 1.0),
			Mth.floor(center.x + (double) diameter + 1.0), Mth.floor(center.y + (double) diameter + 1.0), Mth.floor(center.z + (double) diameter + 1.0));
		Explosion stub = new StubExplosion(tnt, center, radius);
		List<Entity> list = new ArrayList<>();
		for (Entity e : level.getEntities(tnt, box)) {
			if (e.ignoreExplosion(stub)) {
				continue;
			}
			if (Math.sqrt(e.distanceToSqr(center)) / (double) diameter > 1.0) {
				continue;
			}
			list.add(e);
		}
		if (list.size() > MAX_ENTITIES_PER_TNT) {
			list.sort((a, b) -> Double.compare(a.distanceToSqr(center), b.distanceToSqr(center)));
			list = new ArrayList<>(list.subList(0, MAX_ENTITIES_PER_TNT));
		}
		return list;
	}

	private static void entityRays(ClientLevel level, Entity entity, Vec3 center, boolean showBlocked, Sink out) {
		AABB bb = entity.getBoundingBox();
		double sx = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
		double sy = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
		double sz = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
		double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
		double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;
		if (sx < 0.0 || sy < 0.0 || sz < 0.0) {
			return;
		}
		for (double a = 0.0; a <= 1.0; a += sx) {
			for (double b = 0.0; b <= 1.0; b += sy) {
				for (double c = 0.0; c <= 1.0; c += sz) {
					double px = Mth.lerp(a, bb.minX, bb.maxX);
					double py = Mth.lerp(b, bb.minY, bb.maxY);
					double pz = Mth.lerp(c, bb.minZ, bb.maxZ);
					if (out.full()) {
						return;
					}
					Vec3 from = new Vec3(px + ox, py, pz + oz);
					BlockHitResult hit = level.clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
					if (hit.getType() == HitResult.Type.MISS) {
						out.lines.add(new Line(from, center, HIT, 2.0F));
						out.rays++;
					} else if (showBlocked) {
						out.lines.add(new Line(from, hit.getLocation(), BLOCKED, 1.5F));
						out.rays++;
					}
				}
			}
		}
	}

	// ---- blocks: ServerExplosion.calculateExplodedPositions ----

	private static double rayMultiplier(Minecraft mc) {
		if (mc.getSingleplayerServer() != null && OptimizeTntFeature.enabled()) {
			return OptimizeTntFeature.multiplier();
		}
		return Math.max(0.05, Math.min(1.0, KhoaConfig.getInt(ID, "ray_percent", 100) / 100.0));
	}

	private static void blockRays(ClientLevel level, Vec3 center, float radius, double multiplier, Sink out) {
		double acc = 1.0 - multiplier; // same even spreading as the server mixin, so the drawn rays are the rays that really run
		float weak = radius * (0.7F + 0.0F * 0.6F);
		float strong = radius * (0.7F + 1.0F * 0.6F);
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				for (int k = 0; k < 16; k++) {
					if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) {
						continue;
					}
					acc += multiplier;
					if (acc < 1.0) {
						continue;
					}
					acc -= 1.0;
					if (out.full()) {
						return;
					}
					double dx = (double) ((float) i / 15.0F * 2.0F - 1.0F);
					double dy = (double) ((float) j / 15.0F * 2.0F - 1.0F);
					double dz = (double) ((float) k / 15.0F * 2.0F - 1.0F);
					double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
					dx /= len;
					dy /= len;
					dz /= len;
					Vec3 sureEnd = trace(level, center, dx, dy, dz, weak);
					Vec3 maybeEnd = trace(level, center, dx, dy, dz, strong);
					out.lines.add(new Line(center, sureEnd, SURE, 1.0F));
					if (!maybeEnd.equals(sureEnd)) {
						out.lines.add(new Line(sureEnd, maybeEnd, MAYBE, 1.0F));
					}
					out.rays++;
				}
			}
		}
	}

	/** One ray with a fixed strength: same step (0.3), same decay (0.225) and same block resistance as vanilla. */
	private static Vec3 trace(ClientLevel level, Vec3 center, double dx, double dy, double dz, float strength) {
		double x = center.x;
		double y = center.y;
		double z = center.z;
		float s = strength;
		while (s > 0.0F) {
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!level.isInWorldBounds(pos)) {
				break;
			}
			BlockState state = level.getBlockState(pos);
			FluidState fluid = level.getFluidState(pos);
			if (!(state.isAir() && fluid.isEmpty())) {
				float resistance = Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
				s -= (resistance + 0.3F) * 0.3F;
			}
			x += dx * 0.30000001192092896;
			y += dy * 0.30000001192092896;
			z += dz * 0.30000001192092896;
			s -= 0.22500001F;
		}
		return new Vec3(x, y, z);
	}

	/** Only used to ask Entity.ignoreExplosion; there is no ServerLevel on the client. */
	private static final class StubExplosion implements Explosion {
		private final Entity source;
		private final Vec3 center;
		private final float radius;

		StubExplosion(Entity source, Vec3 center, float radius) {
			this.source = source;
			this.center = center;
			this.radius = radius;
		}

		@Override
		public ServerLevel level() {
			return null;
		}

		@Override
		public Explosion.BlockInteraction getBlockInteraction() {
			return Explosion.BlockInteraction.DESTROY;
		}

		@Override
		public LivingEntity getIndirectSourceEntity() {
			return null;
		}

		@Override
		public Entity getDirectSourceEntity() {
			return source;
		}

		@Override
		public float radius() {
			return radius;
		}

		@Override
		public Vec3 center() {
			return center;
		}

		@Override
		public boolean canTriggerBlocks() {
			return false;
		}

		@Override
		public boolean shouldAffectBlocklikeEntities() {
			return true;
		}
	}
}
