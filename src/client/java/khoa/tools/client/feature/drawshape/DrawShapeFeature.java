package khoa.tools.client.feature.drawshape;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Draw Shape: turn it on, right click two blocks. The first click picks the surface (the face you clicked) and
 * corner 1, the second click picks corner 2 (only its position on that surface counts). After that the feature
 * stops reading clicks and draws the shape as a flat overlay on the surface, rebuilt every few ticks.
 */
public final class DrawShapeFeature implements KhoaFeature {
	private static final String ID = "drawshape";
	private static final List<String> SHAPES = List.of("Line", "Rectangle", "Ellipse / Circle", "Diamond");
	/** The overlay is rebuilt every this many ticks (picks up placed blocks and changed settings). */
	private static final int REFRESH_TICKS = 5;
	/** Biggest allowed side of the shape box, in blocks. */
	private static final int MAX_SIDE = 256;
	/** Most cells drawn at once (each cell is one quad). */
	private static final int MAX_DRAWN = 8192;
	/** Lifts the overlay off the surface so it does not flicker against the block. */
	private static final double LIFT = 0.003;

	private enum Stage {
		FIRST, SECOND, DONE
	}

	private record Quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
	}

	private boolean on;
	private Stage stage = Stage.FIRST;
	private boolean wasUse;
	/** Right click is still held from the click that finished the shape: keep blocking it so nothing gets placed. */
	private boolean heldAfterFinish;
	private int ticks;
	private ClientLevel level;

	private Direction face = Direction.UP;
	private Direction.Axis axis = Direction.Axis.Y;
	private int layer;
	private int a1;
	private int b1;
	private int a2;
	private int b2;

	private List<Quad> quads = List.of();
	/** Position of the overlay plane along the surface normal (see rebuild). */
	private double planeC;
	private GizmoStyle style = GizmoStyle.fill(ARGB.color(120, 80, 220, 255));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Draw Shape";
	}

	@Override
	public String description() {
		return "Right click 2 blocks: the 1st click picks the surface and corner 1, the 2nd picks corner 2. Then it draws a Line, Rectangle, Ellipse / Circle (a square box gives a circle) or Diamond on that surface. Turn it off and on, or use Restart, to draw another one.";
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.choice("shape", "Shape", "Line, Rectangle, Ellipse / Circle or Diamond. Changes the drawing live.", SHAPES,
				DrawShapeFeature::shape, v -> KhoaConfig.put(ID, "shape", v)),
			Setting.slider("transparency", "Transparency %", "0 = solid overlay, higher = more see-through.", 0, 95,
				() -> KhoaConfig.getInt(ID, "transparency", 50), v -> KhoaConfig.put(ID, "transparency", v)),
			Setting.bool("filled", "Filled", "On = fill the whole shape, off = only the outline (ignored for Line).",
				() -> KhoaConfig.getBool(ID, "filled", false), v -> KhoaConfig.put(ID, "filled", v)),
			Setting.bool("hide_placed", "Hide placed blocks", "Skip the cells that already have a block in front of the surface, so only what is left to build stays drawn.",
				() -> KhoaConfig.getBool(ID, "hide_placed", true), v -> KhoaConfig.put(ID, "hide_placed", v)),
			Setting.action("restart", "New shape", "Clear the shape and pick 2 new points.", "Restart", this::restart));
	}

	private static int shape() {
		return Math.floorMod(KhoaConfig.getInt(ID, "shape", 1), SHAPES.size());
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		reset();
		KhoaFeature.say(mc, "Draw Shape: " + (on ? "on - right click point 1 (this also picks the surface)" : "off"));
	}

	private void restart() {
		Minecraft mc = Minecraft.getInstance();
		if (!on) {
			on = true;
		}
		reset();
		KhoaFeature.say(mc, "Draw Shape: right click point 1");
	}

	private void reset() {
		stage = Stage.FIRST;
		heldAfterFinish = false;
		quads = List.of();
		ticks = 0;
		level = null;
	}

	@Override
	public void onInit() {
		// Block placing / interacting while picking, so the 2 clicks do not touch the world.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ClientLevel && on && (stage != Stage.DONE || heldAfterFinish)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.END_EXTRACTION.register(context -> draw());
	}

	private void tick(Minecraft mc) {
		if (!on) {
			return;
		}
		if (mc.level == null || mc.player == null) {
			reset();
			return;
		}
		if (level != mc.level) {
			// New world / dimension: the old shape means nothing here.
			reset();
			level = mc.level;
		}

		boolean use = mc.options.keyUse.isDown();
		boolean click = use && !wasUse && mc.screen == null;
		wasUse = use;
		if (heldAfterFinish && !use) {
			heldAfterFinish = false;
		}

		if (stage != Stage.DONE && click && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			pick(mc, hit);
		}

		if (stage == Stage.DONE && ++ticks >= REFRESH_TICKS) {
			ticks = 0;
			rebuild(mc);
		}
	}

	private void pick(Minecraft mc, BlockHitResult hit) {
		if (stage == Stage.FIRST) {
			face = hit.getDirection();
			axis = face.getAxis();
			BlockPos p = hit.getBlockPos().relative(face);
			a1 = a(p);
			b1 = b(p);
			layer = layer(p);
			stage = Stage.SECOND;
			KhoaFeature.say(mc, "Draw Shape: point 1 set - right click point 2");
			rebuild(mc);
			return;
		}
		BlockPos q = hit.getBlockPos();
		if (hit.getDirection().getAxis() == axis) {
			q = q.relative(hit.getDirection());
		}
		int na = a(q);
		int nb = b(q);
		int w = Math.abs(na - a1) + 1;
		int h = Math.abs(nb - b1) + 1;
		if (w > MAX_SIDE || h > MAX_SIDE) {
			KhoaFeature.say(mc, "Draw Shape: too big (" + w + " x " + h + "), max " + MAX_SIDE + " x " + MAX_SIDE + ". Pick point 2 again.");
			return;
		}
		a2 = na;
		b2 = nb;
		stage = Stage.DONE;
		heldAfterFinish = true;
		ticks = 0;
		KhoaFeature.say(mc, "Draw Shape: " + SHAPES.get(shape()) + " " + w + " x " + h);
		rebuild(mc);
	}

	// ---- surface coordinates: a, b = the two axes of the surface, layer = the axis of its normal ----

	private int a(BlockPos p) {
		return switch (axis) {
			case Y -> p.getX();
			case X -> p.getZ();
			case Z -> p.getX();
		};
	}

	private int b(BlockPos p) {
		return switch (axis) {
			case Y -> p.getZ();
			case X -> p.getY();
			case Z -> p.getY();
		};
	}

	private int layer(BlockPos p) {
		return switch (axis) {
			case Y -> p.getY();
			case X -> p.getX();
			case Z -> p.getZ();
		};
	}

	private Vec3 world(double a, double b, double c) {
		return switch (axis) {
			case Y -> new Vec3(a, c, b);
			case X -> new Vec3(c, b, a);
			case Z -> new Vec3(a, b, c);
		};
	}

	private BlockPos cellPos(int a, int b, int c) {
		return switch (axis) {
			case Y -> new BlockPos(a, c, b);
			case X -> new BlockPos(c, b, a);
			case Z -> new BlockPos(a, b, c);
		};
	}

	// ---- shape ----

	private void rebuild(Minecraft mc) {
		ClientLevel world = mc.level;
		if (world == null || stage == Stage.FIRST) {
			quads = List.of();
			return;
		}
		List<int[]> cells;
		boolean hide = false;
		if (stage == Stage.SECOND) {
			cells = List.of(new int[] { a1, b1 });
			style = GizmoStyle.fill(ARGB.color(200, 255, 220, 60));
		} else {
			cells = cells();
			int alpha = Math.round(255 * (100 - KhoaConfig.getInt(ID, "transparency", 50)) / 100.0F);
			style = GizmoStyle.fill(ARGB.color(Math.max(8, Math.min(255, alpha)), 80, 220, 255));
			hide = KhoaConfig.getBool(ID, "hide_placed", true);
		}

		// The overlay sits on the surface: the border between the clicked block and the layer in front of it.
		boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		double c = positive ? layer + LIFT : layer + 1 - LIFT;
		planeC = c;

		// Quads are stored counter-clockwise as seen from the outer side of the surface (draw() flips them
		// when you look from behind), because gizmo quads can be back-face culled.
		Vec3 u = world(1, 0, 0).subtract(world(0, 0, 0));
		Vec3 v = world(0, 1, 0).subtract(world(0, 0, 0));
		boolean flip = u.cross(v).dot(new Vec3(face.getStepX(), face.getStepY(), face.getStepZ())) < 0;

		List<Quad> out = new ArrayList<>();
		for (int[] cell : cells) {
			if (out.size() >= MAX_DRAWN) {
				break;
			}
			if (hide && !world.getBlockState(cellPos(cell[0], cell[1], layer)).isAir()) {
				continue;
			}
			double x0 = cell[0];
			double y0 = cell[1];
			Vec3 p0 = world(x0, y0, c);
			Vec3 p1 = world(x0 + 1, y0, c);
			Vec3 p2 = world(x0 + 1, y0 + 1, c);
			Vec3 p3 = world(x0, y0 + 1, c);
			out.add(flip ? new Quad(p0, p3, p2, p1) : new Quad(p0, p1, p2, p3));
		}
		quads = out;
	}

	private List<int[]> cells() {
		int shape = shape();
		if (shape == 0) {
			return line();
		}
		int amin = Math.min(a1, a2);
		int bmin = Math.min(b1, b2);
		int w = Math.abs(a1 - a2) + 1;
		int h = Math.abs(b1 - b2) + 1;
		boolean filled = KhoaConfig.getBool(ID, "filled", false);

		boolean[][] in = new boolean[w][h];
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				double dx = (i + 0.5 - w / 2.0) / (w / 2.0);
				double dy = (j + 0.5 - h / 2.0) / (h / 2.0);
				in[i][j] = switch (shape) {
					case 1 -> true;
					case 2 -> dx * dx + dy * dy <= 1.0;
					default -> Math.abs(dx) + Math.abs(dy) <= 1.0;
				};
			}
		}

		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				if (!in[i][j]) {
					continue;
				}
				if (!filled && inside(in, i - 1, j) && inside(in, i + 1, j) && inside(in, i, j - 1) && inside(in, i, j + 1)) {
					continue;
				}
				out.add(new int[] { amin + i, bmin + j });
			}
		}
		return out;
	}

	private static boolean inside(boolean[][] in, int i, int j) {
		return i >= 0 && j >= 0 && i < in.length && j < in[0].length && in[i][j];
	}

	/** Bresenham between the two corner cells. */
	private List<int[]> line() {
		List<int[]> out = new ArrayList<>();
		int x = a1;
		int y = b1;
		int dx = Math.abs(a2 - a1);
		int dy = Math.abs(b2 - b1);
		int sx = a1 < a2 ? 1 : -1;
		int sy = b1 < b2 ? 1 : -1;
		int err = dx - dy;
		while (true) {
			out.add(new int[] { x, y });
			if (x == a2 && y == b2) {
				break;
			}
			int e2 = 2 * err;
			if (e2 > -dy) {
				err -= dy;
				x += sx;
			}
			if (e2 < dx) {
				err += dx;
				y += sy;
			}
		}
		return out;
	}

	private void draw() {
		if (!on || quads.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition();
		double e = switch (axis) {
			case X -> eye.x;
			case Y -> eye.y;
			case Z -> eye.z;
		};
		boolean outside = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? e > planeC : e < planeC;
		for (Quad q : quads) {
			if (outside) {
				Gizmos.rect(q.p0(), q.p1(), q.p2(), q.p3(), style);
			} else {
				Gizmos.rect(q.p0(), q.p3(), q.p2(), q.p1(), style);
			}
		}
	}
}
