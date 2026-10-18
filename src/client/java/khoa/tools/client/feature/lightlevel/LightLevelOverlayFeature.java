package khoa.tools.client.feature.lightlevel;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.Vec3;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Light Level Overlay: paints red every surface (top of a solid block with free space above it) whose BLOCK light
 * level is at or below the slider. Sky light is ignored. Rebuilt on every block update, light update and when you move.
 */
public final class LightLevelOverlayFeature implements KhoaFeature {
	private static final String ID = "lightlevel";
	private static final int MIN_RANGE = 16;
	private static final int MAX_RANGE = 48;
	/** Vertical scan radius is at most this (the horizontal radius is the Display Range). */
	private static final int MAX_VERTICAL = 16;
	/** Most surfaces drawn at once (each one is a quad). */
	private static final int MAX_CELLS = 20000;
	/** Lifts the red overlay off the block so it does not flicker against it. */
	private static final double LIFT = 0.003;
	/** After a block / light update, keep rebuilding this many ticks so the light engine can catch up. */
	private static final int SETTLE_TICKS = 4;
	/** Never rebuild more often than every this many ticks. */
	private static final int MIN_INTERVAL = 2;
	/** Rebuild when you moved at least this far (blocks) from where the overlay was built. */
	private static final int MOVE_DISTANCE = 3;

	private static final GizmoStyle STYLE = GizmoStyle.fill(ARGB.color(110, 255, 40, 40));

	private static volatile boolean dirty;

	private record Cell(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double y) {
	}

	private boolean on;
	private List<Cell> cells = List.of();
	private boolean needsRebuild;
	private int settle;
	private int sinceRebuild;
	private ClientLevel level;
	private BlockPos builtAt = BlockPos.ZERO;
	private int builtLevel = -1;
	private int builtRange = -1;

	/** Called from the mixins: something changed that can change light or surfaces. */
	public static void markDirty() {
		dirty = true;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Light Level Overlay";
	}

	@Override
	public String description() {
		return "Paints red every surface whose block light level is at or below the slider (sky light is ignored). Updates on every block / light update and when you move.";
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.slider("level", "Red light level", "Surfaces with block light level at or below this get the red overlay (0 - 15).", 0, 15,
				LightLevelOverlayFeature::threshold, v -> KhoaConfig.put(ID, "level", v)),
			Setting.slider("range", "Display range", "How many blocks around you are scanned (horizontal radius, min 16). Vertical range is limited to 16. Bigger = more lag.", MIN_RANGE, MAX_RANGE,
				LightLevelOverlayFeature::range, v -> KhoaConfig.put(ID, "range", v)));
	}

	private static int threshold() {
		return Math.max(0, Math.min(15, KhoaConfig.getInt(ID, "level", 0)));
	}

	private static int range() {
		return Math.max(MIN_RANGE, Math.min(MAX_RANGE, KhoaConfig.getInt(ID, "range", MIN_RANGE)));
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		cells = List.of();
		needsRebuild = on;
		settle = 0;
		sinceRebuild = MIN_INTERVAL;
		builtLevel = -1;
		builtRange = -1;
		KhoaFeature.say(mc, "Light Level Overlay: " + (on ? "on (block light <= " + threshold() + ")" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.END_EXTRACTION.register(context -> draw());
	}

	private void tick(Minecraft mc) {
		if (!on) {
			dirty = false;
			return;
		}
		if (mc.level == null || mc.player == null) {
			cells = List.of();
			level = null;
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			cells = List.of();
			needsRebuild = true;
		}

		if (dirty) {
			dirty = false;
			settle = SETTLE_TICKS;
			needsRebuild = true;
		} else if (settle > 0) {
			settle--;
			needsRebuild = true;
		}

		BlockPos here = mc.player.blockPosition();
		if (Math.abs(here.getX() - builtAt.getX()) >= MOVE_DISTANCE || Math.abs(here.getY() - builtAt.getY()) >= MOVE_DISTANCE
			|| Math.abs(here.getZ() - builtAt.getZ()) >= MOVE_DISTANCE || threshold() != builtLevel || range() != builtRange) {
			needsRebuild = true;
		}

		if (sinceRebuild < MIN_INTERVAL) {
			sinceRebuild++;
		}
		if (needsRebuild && sinceRebuild >= MIN_INTERVAL) {
			rebuild(mc, here);
			needsRebuild = false;
			sinceRebuild = 0;
		}
	}

	private void rebuild(Minecraft mc, BlockPos center) {
		ClientLevel world = mc.level;
		if (world == null) {
			cells = List.of();
			return;
		}
		int threshold = threshold();
		int r = range();
		builtAt = center;
		builtLevel = threshold;
		builtRange = r;

		int minY = world.getMinY();
		int maxY = minY + world.getHeight() - 1;
		int v = Math.min(r, MAX_VERTICAL);
		int y0 = Math.max(minY, center.getY() - v);
		int y1 = Math.min(maxY - 1, center.getY() + v);

		LayerLightEventListener light = world.getLightEngine().getLayerListener(LightLayer.BLOCK);
		BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
		List<Cell> out = new ArrayList<>();

		columns:
		for (int x = center.getX() - r; x <= center.getX() + r; x++) {
			for (int z = center.getZ() - r; z <= center.getZ() + r; z++) {
				if (!world.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				belowPos.set(x, y0, z);
				BlockState below = world.getBlockState(belowPos);
				for (int y = y0; y <= y1; y++) {
					belowPos.set(x, y, z);
					abovePos.set(x, y + 1, z);
					BlockState above = world.getBlockState(abovePos);
					if (!below.isAir() && isFree(world, above, abovePos) && below.isFaceSturdy(world, belowPos, Direction.UP)
						&& light.getLightValue(abovePos) <= threshold) {
						double top = y + 1 + LIFT;
						// Counter-clockwise as seen from above (draw() flips it when you look from below).
						out.add(new Cell(new Vec3(x, top, z), new Vec3(x, top, z + 1), new Vec3(x + 1, top, z + 1), new Vec3(x + 1, top, z), top));
						if (out.size() >= MAX_CELLS) {
							break columns;
						}
					}
					below = above;
				}
			}
		}
		cells = out;
	}

	/** Empty space a mob could stand in: no collision and no fluid. */
	private static boolean isFree(ClientLevel world, BlockState state, BlockPos pos) {
		if (state.isAir()) {
			return true;
		}
		return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	private void draw() {
		if (!on || cells.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		double eyeY = mc.player.getEyePosition().y;
		for (Cell c : cells) {
			if (eyeY > c.y()) {
				Gizmos.rect(c.p0(), c.p1(), c.p2(), c.p3(), STYLE);
			} else {
				Gizmos.rect(c.p0(), c.p3(), c.p2(), c.p1(), STYLE);
			}
		}
	}
}
