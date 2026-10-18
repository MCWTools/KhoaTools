package khoa.locator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;

import java.util.List;

/** /locatortrace: bật/tắt, tính path tới target của /locatorfinder, tính lại mỗi repeatDelay tick (0 = chỉ 1 lần). */
public final class PathPlanner {
	public static final int DEFAULT_DELAY = 100;
	private static final int NODES_PER_TICK = 400;
	private static final double DIRECTION_GOAL_DISTANCE = 150.0;

	private static volatile boolean enabled = false;
	private static volatile int repeatDelay = DEFAULT_DELAY;
	private static volatile List<Vec3> path = null;
	private static volatile boolean[] narrow = null;
	private static Vec3 lastGoal = null;
	private static volatile boolean reached = false;
	private static volatile String status = "";
	private static PathSearch search = null;
	private static int timer = 0;

	private PathPlanner() {}

	public static boolean enabled() { return enabled; }
	public static int repeatDelay() { return repeatDelay; }
	public static List<Vec3> path() { return path; }
	public static boolean[] narrow() { return narrow; }
	public static boolean reachedGoal() { return reached; }
	public static String status() { return enabled ? status : ""; }

	public static void setEnabled(boolean on) {
		enabled = on;
		invalidate();
		if (!on) status = "";
	}

	public static void setRepeatDelay(int ticks) { repeatDelay = Math.max(0, ticks); }

	/** Gọi khi đổi target hoặc tắt locatorfinder. */
	public static void invalidate() {
		search = null;
		path = null;
		narrow = null;
		lastGoal = null;
		timer = 0;
		reached = false;
		status = enabled ? "đang tính path..." : "";
	}

	public static void tick(Minecraft mc) {
		if (!enabled) return;
		if (mc.player == null || mc.level == null || !LocatorTracker.active()) {
			path = null;
			search = null;
			status = "chưa có target";
			return;
		}
		if (search != null) {
			if (search.step(NODES_PER_TICK)) finish();
			return;
		}
		boolean due = path == null || (repeatDelay > 0 && ++timer >= repeatDelay);
		if (due) start(mc);
	}

	private static void start(Minecraft mc) {
		Vec3 goal = resolveGoal(mc);
		if (goal == null) { status = "chưa biết đích (không có waypoint)"; return; }
		timer = 0;
		PathSearch ps = new PathSearch(mc.level, mc.player.position(), goal);
		// Nhanh: path cũ đã tới đích và đích không dịch xa -> chỉ kiểm tra lại thay vì chạy A*
		if (path != null && reached && lastGoal != null && lastGoal.distanceToSqr(goal) < 36.0) {
			PathSearch.Result r = ps.tryReuse(path);
			if (r != null) { apply(r, " · dùng lại path cũ"); return; }
		}
		lastGoal = goal;
		search = ps;
		if (path == null) status = "đang tính path...";
	}

	private static void finish() {
		PathSearch.Result r = search.result();
		search = null;
		apply(r, "");
	}

	private static void apply(PathSearch.Result r, String suffix) {
		path = r.points();
		narrow = r.narrow();
		reached = r.reachedGoal();
		double len = 0, narrowLen = 0;
		for (int i = 1; i < path.size(); i++) {
			double d = path.get(i).distanceTo(path.get(i - 1));
			len += d;
			if (narrow[i - 1]) narrowLen += d;
		}
		String narrowTxt = " (hẹp " + Math.round(narrowLen) + "m)";
		status = reached
			? "path: " + Math.round(len) + " block tới đích" + narrowTxt + suffix
			: "path: bị chặn, tới điểm gần nhất (còn " + Math.round(r.remaining()) + " block tới đích)" + narrowTxt;
	}

	private static Vec3 resolveGoal(Minecraft mc) {
		Player loaded = mc.level.getPlayerByUUID(LocatorTracker.targetId());
		if (loaded != null) return loaded.position();
		Vec3 known = LocatorTracker.knownPos(mc.player.getY());
		if (known != null) return known;
		// chỉ có hướng: đặt đích giả cách DIRECTION_GOAL_DISTANCE block theo hướng đó
		TrackedWaypoint wp = LocatorTracker.waypoint();
		if (wp == null) return null;
		double angle = wp.yawAngleToCamera(mc.level, mc.gameRenderer.getMainCamera(), e -> 1.0F);
		if (Double.isNaN(angle)) return null;
		double yawRad = Math.toRadians(mc.gameRenderer.getMainCamera().yaw() + angle);
		return mc.player.position().add(-Math.sin(yawRad) * DIRECTION_GOAL_DISTANCE, 0, Math.cos(yawRad) * DIRECTION_GOAL_DISTANCE);
	}
}
