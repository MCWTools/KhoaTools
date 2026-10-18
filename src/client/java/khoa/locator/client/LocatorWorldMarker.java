package khoa.locator.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.TrackedWaypoint.PitchDirection;

/**
 * Marker trong world (Gizmos, xuyên tường), đặt theo thứ tự ưu tiên:
 *  1. target đang load ở client -> vị trí thật
 *  2. server gửi coord block   -> đúng block đó
 *  3. server gửi chunk         -> tâm chunk (Y = Y của mình)
 *  4. chỉ có hướng             -> 12 block theo hướng ngang
 */
public final class LocatorWorldMarker {
	private static final double DIRECTION_DISTANCE = 12.0;
	private static final double MAX_DRAW_DISTANCE = 48.0;

	private LocatorWorldMarker() {}

	public static void render(LevelRenderContext context) {
		if (!LocatorTracker.active()) return;
		Minecraft mc = Minecraft.getInstance();
		Entity camEntity = mc.getCameraEntity();
		TrackedWaypoint wp = LocatorTracker.waypoint();
		if (camEntity == null || wp == null || mc.level == null) return;

		Level level = camEntity.level();
		Camera camera = mc.gameRenderer.getMainCamera();
		DeltaTracker delta = mc.getDeltaTracker();
		PartialTickSupplier pts = e -> delta.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(e));
		Vec3 camPos = camera.position();

		int color = ARGB.opaque(wp.icon().color.orElseGet(
			() -> ARGB.setBrightness(ARGB.color(255, LocatorTracker.targetId().hashCode()), 0.9F)));

		Vec3 real = null;
		String tag = "";
		Player loaded = level.getPlayerByUUID(LocatorTracker.targetId());
		if (loaded != null) {
			real = loaded.getEyePosition(pts.apply(loaded));
		} else {
			real = LocatorTracker.knownPos(camPos.y);
			if (LocatorTracker.mode() == LocatorTracker.Mode.CHUNK) tag = " ~chunk";
		}

		Vec3 pos;
		String dist;
		String vertical = "";
		if (real != null) {
			Vec3 dir = real.subtract(camPos);
			double d = dir.length();
			pos = d > MAX_DRAW_DISTANCE ? camPos.add(dir.scale(MAX_DRAW_DISTANCE / d)) : real;
			dist = Math.round(d) + "m";
		} else {
			double angle = wp.yawAngleToCamera(level, camera, pts); // + = bên phải
			if (Double.isNaN(angle)) return;
			PitchDirection pitch = wp.pitchDirectionToCamera(level, mc.gameRenderer, pts);
			// Yaw đích = yaw camera + góc lệch (rotateClockwise90 của Vec3 là (x,z)->(-z,x), khớp quy ước yaw của MC)
			double yawRad = Math.toRadians(camera.yaw() + angle);
			double dy = pitch == PitchDirection.UP ? 0.35 : pitch == PitchDirection.DOWN ? -0.35 : 0.0;
			pos = camPos.add(new Vec3(-Math.sin(yawRad), dy, Math.cos(yawRad)).normalize().scale(DIRECTION_DISTANCE));
			double d2 = wp.distanceSquared(camEntity);
			dist = Double.isInfinite(d2) ? "xa" : Math.round(Math.sqrt(d2)) + "m";
			vertical = pitch == PitchDirection.UP ? " ⬆" : pitch == PitchDirection.DOWN ? " ⬇" : "";
			tag = " (hướng)";
		}

		Gizmos.line(pos.add(0, -2.0, 0), pos.add(0, 2.0, 0), color, 4.0F).setAlwaysOnTop();
		Gizmos.point(pos, color, 14.0F).setAlwaysOnTop();
		Gizmos.billboardText(LocatorTracker.targetName() + " " + dist + vertical + tag,
			pos.add(0, 2.4, 0), TextGizmo.Style.forColorAndCentered(color).withScale(0.5F)).setAlwaysOnTop();
	}
}
