package khoa.tools.client.feature.optimizefps;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Optimize FPS (includes the old Entity Limiter). Off by default; click the card to turn it on in laggy areas
 * (thousands of spawners / mobs). Defaults of the settings keep everything vanilla.
 */
public class OptimizeFpsFeature implements KhoaFeature {
	private static boolean active = false;
	private static final IntOpenHashSet ALLOWED = new IntOpenHashSet();

	/** Animation level 2: block entity render states are re-extracted at most every 100 ms (~10 FPS). */
	private static final long ANIM_INTERVAL_L2_NANOS = 100_000_000L;
	/** Animation level 1: every 200 ms (~5 FPS). */
	private static final long ANIM_INTERVAL_L1_NANOS = 200_000_000L;
	/** Animation level 1: block entities farther than this (blocks) are not rendered. */
	private static final double ANIM_RANGE_SQR = 32.0 * 32.0;
	/** Particle level 1: particles farther than this (blocks) from the player are not created. */
	private static final double PARTICLE_RANGE_SQR = 16.0 * 16.0;

	private static final class Cached {
		BlockEntityRenderState state;
		BlockState blockState;
		long nanos;
	}

	private static final Reference2ObjectOpenHashMap<BlockEntity, Cached> CACHE = new Reference2ObjectOpenHashMap<>();

	@Override
	public String id() {
		return "optimizefps";
	}

	@Override
	public String displayName() {
		return "Optimize FPS";
	}

	@Override
	public String description() {
		return "Use if youre near a lot of block that has animation , particle , entity.";
	}

	/** Block animation level 0-3 (3 = keep everything). */
	public static int animationLevel() {
		return active ? KhoaConfig.getInt("optimizefps", "animation", 3) : 3;
	}

	/** Particle level 0-3 (3 = keep everything). */
	public static int particleLevel() {
		return active ? KhoaConfig.getInt("optimizefps", "particle", 3) : 3;
	}

	/** -1 = no limit, 0 = hide all non-player entities, N = show N nearest. */
	public static int entityLimit() {
		return active ? KhoaConfig.getInt("optimizefps", "entity", -1) : -1;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.slider("animation", "Animation Strength",
				"3 = keep all block animation. 2 = lower animation FPS. 1 = lower animation FPS and render range. 0 = no block animation.",
				0, 3, () -> KhoaConfig.getInt(id(), "animation", 3), v -> KhoaConfig.put(id(), "animation", v)),
			Setting.slider("particle", "Particle",
				"3 = keep all particles. 2 = half the particles. 1 = lower particle render distance. 0 = no particles.",
				0, 3, () -> KhoaConfig.getInt(id(), "particle", 3), v -> KhoaConfig.put(id(), "particle", v)),
			Setting.integer("entity", "Entity",
				"Only render this many entities, nearest first (players always shown). -1 = no limit, 0 = hide all other entities.",
				-1, 99999, () -> KhoaConfig.getInt(id(), "entity", -1), v -> KhoaConfig.put(id(), "entity", v)),
			Setting.bool("optimized_spawner", "Optimized Spawner",
				"Hides the model and the particles of spawners you can't see: farther than Spinning Model Range from the camera (freecam included), or behind a solid block / another spawner.",
				() -> KhoaConfig.getBool(id(), "optimized_spawner", false), v -> {
					KhoaConfig.put(id(), "optimized_spawner", v);
					resetSpawnerCaches();
				}),
			Setting.slider("spinning_range", "Spinning Model Range",
				"Optimized Spawner: spawners farther than this many blocks from the camera are hidden (model and particles).",
				2, 32, () -> spinningRange(), v -> KhoaConfig.put(id(), "spinning_range", v)),
			Setting.bool("spawner_2d", "2D Spinning Model",
				"Spawners show a spinning 2D spawn egg of their mob instead of the whole mob model. Mobs without a spawn egg are hidden.",
				() -> KhoaConfig.getBool(id(), "spawner_2d", false), v -> {
					KhoaConfig.put(id(), "spawner_2d", v);
					resetSpawnerCaches(); // cached render states still hold the old (mob / egg) data, drop them so the change shows at once
				}));
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
		resetSpawnerCaches();
		recompute(mc);
		KhoaFeature.say(mc, "Optimize FPS: " + (active ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(OptimizeFpsFeature::recompute);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { EGGS.clear(); VISIBLE.clear(); VISIBLE_AT.clear(); });
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { EGGS.clear(); VISIBLE.clear(); VISIBLE_AT.clear(); });
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.executes(c -> {
				activate(c.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("animation").then(ClientCommands.argument("level", IntegerArgumentType.integer(0, 3)).executes(c -> {
				KhoaConfig.put(id(), "animation", IntegerArgumentType.getInteger(c, "level"));
				return 1;
			})))
			.then(ClientCommands.literal("particle").then(ClientCommands.argument("level", IntegerArgumentType.integer(0, 3)).executes(c -> {
				KhoaConfig.put(id(), "particle", IntegerArgumentType.getInteger(c, "level"));
				return 1;
			})))
			.then(ClientCommands.literal("entity").then(ClientCommands.argument("num", IntegerArgumentType.integer(-1, 99999)).executes(c -> {
				KhoaConfig.put(id(), "entity", IntegerArgumentType.getInteger(c, "num"));
				recompute(c.getSource().getClient());
				return 1;
			}))));
	}

	private static void recompute(Minecraft mc) {
		if (mc.level == null || animationLevel() >= 3 || (mc.level.getGameTime() % 100) == 0) {
			pruneCache();
		}
		ALLOWED.clear();
		int limit = entityLimit();
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

	/** Called from EntityRenderDispatcherMixin. */
	public static boolean isEntityHidden(Entity e) {
		int limit = entityLimit();
		if (limit < 0 || e instanceof Player) return false;
		LocalPlayer self = Minecraft.getInstance().player;
		if (self != null && e == self.getVehicle()) return false;
		return limit == 0 || !ALLOWED.contains(e.getId());
	}

	// ---- block entity animation (BlockEntityRenderDispatcherMixin) ----

	private static void pruneCache() {
		if (CACHE.isEmpty()) return;
		if (animationLevel() >= 3) {
			CACHE.clear();
			return;
		}
		CACHE.keySet().removeIf(BlockEntity::isRemoved);
	}

	/**
	 * Called at the head of BlockEntityRenderDispatcher.tryExtractRenderState.
	 * Returns true when the result is decided here: {@code out[0]} is then the state to use (null = do not render).
	 * Level 2: reuse the last state for 100 ms. Level 1: for 200 ms, and block entities beyond 32 blocks are skipped.
	 * Level 0: animation frozen (the first extracted state is reused until the block state changes).
	 */
	public static boolean blockEntityOverride(BlockEntity be, ModelFeatureRenderer.CrumblingOverlay overlay, BlockEntityRenderState[] out) {
		int level = animationLevel();
		if (level >= 3 || overlay != null) return false; // never touch a block that is being broken

		if (level == 1) {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p != null && be.getBlockPos().distToCenterSqr(p.getX(), p.getY(), p.getZ()) > ANIM_RANGE_SQR) {
				out[0] = null;
				return true;
			}
		}

		Cached c = CACHE.get(be);
		if (c == null || c.state == null || c.blockState != be.getBlockState()) return false;
		if (level == 0 || System.nanoTime() - c.nanos < (level == 1 ? ANIM_INTERVAL_L1_NANOS : ANIM_INTERVAL_L2_NANOS)) {
			out[0] = c.state;
			return true;
		}
		return false;
	}

	/** Called at the return of tryExtractRenderState with the freshly extracted state. */
	public static void blockEntityExtracted(BlockEntity be, BlockEntityRenderState state, ModelFeatureRenderer.CrumblingOverlay overlay) {
		if (state == null || overlay != null || animationLevel() >= 3) return;
		Cached c = CACHE.get(be);
		if (c == null) {
			c = new Cached();
			CACHE.put(be, c);
		} else if (c.state == state) {
			return; // this is the cached state itself being returned, keep its timestamp
		}
		c.state = state;
		c.blockState = be.getBlockState();
		c.nanos = System.nanoTime();
	}

	// ---- particles (ParticleEngineMixin) ----

	/**
	 * True = do not create this particle. Level 2: half of them. Level 1: half, and none farther than 16 blocks.
	 * Level 0: none.
	 */
	public static boolean skipParticle(double x, double y, double z) {
		if (optimizedSpawner()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				BlockPos bp = BlockPos.containing(x, y, z);
				BlockState bs = mc.level.getBlockState(bp);
				if ((bs.is(Blocks.SPAWNER) || bs.is(Blocks.TRIAL_SPAWNER)) && !spawnerVisible(bp)) return true;
			}
		}
		int level = particleLevel();
		if (level >= 3) return false;
		if (level <= 0) return true;
		if (ThreadLocalRandom.current().nextBoolean()) return true; // random: particles come in pairs (smoke+flame), a counter would always drop the same kind
		if (level == 1) {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p != null) {
				double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z;
				return dx * dx + dy * dy + dz * dz > PARTICLE_RANGE_SQR;
			}
		}
		return false;
	}

	// ---- optimized spawner model (TrialSpawnerRendererMixin / SpawnerRendererMixin) ----

	private static final ItemStackRenderState NO_EGG = new ItemStackRenderState();
	/** Spawn egg model per mob type, shared by every spawner (render states only read it). Cleared on join/disconnect. */
	private static final java.util.IdentityHashMap<EntityType<?>, ItemStackRenderState> EGGS = new java.util.IdentityHashMap<>();

	public static boolean optimizedSpawner() {
		return active && KhoaConfig.getBool("optimizefps", "optimized_spawner", false);
	}

	public static boolean spawner2dModel() {
		return active && KhoaConfig.getBool("optimizefps", "spawner_2d", false);
	}

	private static void resetSpawnerCaches() {
		CACHE.clear();
		VISIBLE.clear();
		VISIBLE_AT.clear();
	}

	/** True = the spawner at this state's position is not seen, draw nothing for it. */
	public static boolean spawnerHidden(SpawnerRenderState state) {
		return optimizedSpawner() && !spawnerVisible(state.blockPos);
	}

	/** The flat spawn egg model for this mob, or null when the mob has no spawn egg (then the spawner shows nothing inside). */
	public static ItemStackRenderState spawnEggFor(EntityType<?> type) {
		ItemStackRenderState cached = EGGS.get(type);
		if (cached != null) {
			return cached == NO_EGG ? null : cached;
		}
		Minecraft mc = Minecraft.getInstance();
		Optional<Holder<Item>> egg = SpawnEggItem.byId(type);
		if (egg.isEmpty() || mc.level == null) {
			if (mc.level != null) {
				EGGS.put(type, NO_EGG);
			}
			return null;
		}
		ItemStackRenderState state = new ItemStackRenderState();
		mc.getItemModelResolver().updateForTopItem(state, new ItemStack(egg.get()), ItemDisplayContext.FIXED, mc.level, null, 0);
		EGGS.put(type, state);
		return state;
	}

	/** Draws the egg, spinning like the mob did, at the same spot (block centre). */
	public static void submitSpawnerEgg(SpawnerRenderState renderState, PoseStack pose, SubmitNodeCollector collector) {
		ItemStackRenderState egg = ((SpawnerEggHolder) (Object) renderState).khoa$getEgg();
		if (egg == null || egg.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5F, 0.5F, 0.5F);
		pose.mulPose(Axis.YP.rotationDegrees(renderState.spin));
		egg.submit(pose, collector, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	// ---- spinning model range + occlusion (checked from the camera, so freecam works) ----

	private static final long VISIBILITY_INTERVAL_NANOS = 100_000_000L;
	private static final Long2BooleanOpenHashMap VISIBLE = new Long2BooleanOpenHashMap();
	private static final Long2LongOpenHashMap VISIBLE_AT = new Long2LongOpenHashMap();

	public static int spinningRange() {
		return Math.max(2, Math.min(32, KhoaConfig.getInt("optimizefps", "spinning_range", 16)));
	}

	/** False when the spawner is beyond the range from the camera, or a solid block / another spawner is on the line camera -> spawner. */
	private static boolean spawnerVisible(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null) return true;
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 cam = camera.position();
		double tx = pos.getX() + 0.5, ty = pos.getY() + 0.5, tz = pos.getZ() + 0.5;
		double dx = tx - cam.x, dy = ty - cam.y, dz = tz - cam.z;
		double range = spinningRange();
		if (dx * dx + dy * dy + dz * dz > range * range) return false;

		long key = pos.asLong();
		long now = System.nanoTime();
		if (VISIBLE.size() > 50_000) {
			VISIBLE.clear();
			VISIBLE_AT.clear();
		}
		if (VISIBLE.containsKey(key) && now - VISIBLE_AT.get(key) < VISIBILITY_INTERVAL_NANOS) {
			return VISIBLE.get(key);
		}
		boolean visible = !lineBlocked(level, cam.x, cam.y, cam.z, tx, ty, tz, pos);
		VISIBLE.put(key, visible);
		VISIBLE_AT.put(key, now);
		return visible;
	}

	/** Voxel walk from (cx,cy,cz) to the spawner centre. The camera cell and the target cell are not tested. */
	private static boolean lineBlocked(Level level, double cx, double cy, double cz, double tx, double ty, double tz, BlockPos target) {
		double dx = tx - cx, dy = ty - cy, dz = tz - cz;
		int x = Mth.floor(cx), y = Mth.floor(cy), z = Mth.floor(cz);
		int ex = target.getX(), ey = target.getY(), ez = target.getZ();
		int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
		double inf = Double.POSITIVE_INFINITY;
		double tdx = dx == 0 ? inf : Math.abs(1.0 / dx);
		double tdy = dy == 0 ? inf : Math.abs(1.0 / dy);
		double tdz = dz == 0 ? inf : Math.abs(1.0 / dz);
		double tmx = dx == 0 ? inf : (dx > 0 ? (x + 1 - cx) : (cx - x)) * tdx;
		double tmy = dy == 0 ? inf : (dy > 0 ? (y + 1 - cy) : (cy - y)) * tdy;
		double tmz = dz == 0 ? inf : (dz > 0 ? (z + 1 - cz) : (cz - z)) * tdz;
		BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 128; i++) {
			if (tmx <= tmy && tmx <= tmz) {
				if (tmx > 1.0) return false;
				x += sx;
				tmx += tdx;
			} else if (tmy <= tmz) {
				if (tmy > 1.0) return false;
				y += sy;
				tmy += tdy;
			} else {
				if (tmz > 1.0) return false;
				z += sz;
				tmz += tdz;
			}
			if (x == ex && y == ey && z == ez) return false;
			BlockState bs = level.getBlockState(cell.set(x, y, z));
			if (bs.isSolidRender() || bs.is(Blocks.SPAWNER) || bs.is(Blocks.TRIAL_SPAWNER)) return true;
		}
		return false;
	}
}
