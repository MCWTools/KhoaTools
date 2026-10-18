package khoa.tools.client.feature.findleak;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Stand inside a closed room, turn it on, and every hole to the outside (open sky, big open space, unloaded area) gets a red box.
 * Holes that touch are merged into one box. Pockets behind an opening that are closed again (closets, corridors, other rooms) are
 * not leaks. The algorithm lives in {@link LeakScanner}.
 */
public final class FindLeakFeature implements KhoaFeature {
	private static final String ID = "findleak";
	private static final int STROKE = ARGB.color(255, 255, 50, 50);
	private static final int FILL = ARGB.color(60, 255, 50, 50);
	private static final long TICK_BUDGET_NANOS = 6_000_000L;

	private static boolean on;
	private static LeakScanner scanner;
	private static ClientLevel scanLevel;
	private static int scanDistance;
	private static volatile List<AABB> boxes = List.of();

	private static int maxDistance() {
		return Math.max(10, Math.min(128, KhoaConfig.getInt(ID, "max_distance", 32)));
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Find Leak";
	}

	@Override
	public String description() {
		return "Stand inside a closed room and turn this on: every hole to the outside (open sky, open space, unloaded area) is boxed in red, "
			+ "touching holes are merged into one box. Closed pockets behind an opening (closets, corridors) are not leaks. "
			+ "Set Max Distance to about the size of the room (bigger = slower).";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.slider("max_distance", "Max Distance", "How far (blocks, each direction) the scan may go. Anything beyond it counts as outside.", 10, 128,
				FindLeakFeature::maxDistance, v -> KhoaConfig.put(ID, "max_distance", v)),
			Setting.action("rescan", "Rescan", "Scan again from where you stand (after you patched a hole).", "Rescan",
				() -> {
					if (on) {
						start(Minecraft.getInstance());
					}
				}));
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		if (on) {
			off();
			KhoaFeature.say(mc, "Find Leak: off");
			return;
		}
		if (mc.level == null || mc.player == null) {
			return;
		}
		on = true;
		start(mc);
	}

	private static void start(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		scanLevel = mc.level;
		scanDistance = maxDistance();
		boxes = List.of();
		scanner = new LeakScanner(mc.level, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), scanDistance);
		KhoaFeature.say(mc, "Find Leak: scanning (max distance " + scanDistance + ")...");
	}

	private static void off() {
		on = false;
		scanner = null;
		scanLevel = null;
		boxes = List.of();
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(FindLeakFeature::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> off());
		LevelRenderEvents.END_EXTRACTION.register(context -> draw());
	}

	private static void tick(Minecraft mc) {
		if (!on) {
			return;
		}
		if (mc.level == null || mc.player == null || mc.level != scanLevel) {
			off();
			return;
		}
		LeakScanner s = scanner;
		if (s == null || !s.step(System.nanoTime() + TICK_BUDGET_NANOS)) {
			return;
		}
		scanner = null;
		if (s.error() != null) {
			KhoaFeature.say(mc, "Find Leak: " + s.error());
			return;
		}
		boxes = s.boxes();
		if (boxes.isEmpty()) {
			KhoaFeature.say(mc, "Find Leak: no leak found within " + scanDistance + " blocks (" + s.visited() + " cells checked).");
		} else {
			KhoaFeature.say(mc, "Find Leak: " + boxes.size() + " leak spot(s) (" + s.holes() + " hole(s) before merging)"
				+ (s.truncated() ? ", stopped early: too many holes" : "") + "."
				+ (s.hitLimit() ? " Some openings lead past the max distance edge: raise Max Distance if that is still inside your room." : ""));
		}
	}

	private static void draw() {
		if (!on) {
			return;
		}
		List<AABB> list = boxes;
		if (list.isEmpty()) {
			return;
		}
		GizmoStyle style = GizmoStyle.strokeAndFill(STROKE, 3.0F, FILL);
		for (AABB box : list) {
			Gizmos.cuboid(box, style).setAlwaysOnTop();
		}
	}
}
