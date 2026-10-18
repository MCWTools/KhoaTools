package khoa.locator.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Vẽ path 3D bằng Gizmos (xuyên tường): xanh = chỗ thoáng, đỏ = chỗ hẹp, chấm cam to ở cuối nếu path chưa tới đích. */
public final class LocatorPathRenderer {
	private static final int GREEN = ARGB.color(255, 80, 255, 120);
	private static final int RED = ARGB.color(255, 255, 60, 60);
	private static final int ORANGE = ARGB.color(255, 255, 170, 40);

	private LocatorPathRenderer() {}

	public static void render(LevelRenderContext context) {
		if (!PathPlanner.enabled()) return;
		List<Vec3> pts = PathPlanner.path();
		boolean[] narrow = PathPlanner.narrow();
		if (pts == null || narrow == null || pts.size() < 2 || narrow.length < pts.size() - 1) return;
		for (int i = 1; i < pts.size(); i++) {
			int color = narrow[i - 1] ? RED : GREEN;
			Gizmos.line(pts.get(i - 1), pts.get(i), color, 3.0F).setAlwaysOnTop();
			Gizmos.point(pts.get(i), color, 8.0F).setAlwaysOnTop();
		}
		if (!PathPlanner.reachedGoal()) Gizmos.point(pts.get(pts.size() - 1), ORANGE, 16.0F).setAlwaysOnTop();
	}
}
