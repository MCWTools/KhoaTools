package khoa.locator.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.TrackedWaypoint.PitchDirection;

public final class LocatorOverlay {
	private LocatorOverlay() {}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!LocatorTracker.active()) return;
		Minecraft mc = Minecraft.getInstance();
		Entity camEntity = mc.getCameraEntity();
		if (camEntity == null || mc.options.hideGui) return;

		int cx = graphics.guiWidth() / 2;
		int y = 22;
		String name = LocatorTracker.targetName();
		TrackedWaypoint wp = LocatorTracker.waypoint();

		if (wp == null) {
			graphics.centeredText(mc.font, name + ": không có waypoint (xa quá / đã tắt locator)", cx, y, 0xFFFF5555);
			return;
		}

		Level level = camEntity.level();
		PartialTickSupplier pts = e -> deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(e));
		double angle = wp.yawAngleToCamera(level, mc.gameRenderer.getMainCamera(), pts); // + = bên phải
		if (Double.isNaN(angle)) {
			graphics.centeredText(mc.font, name + ": chưa có vị trí", cx, y, 0xFFFFAA00);
			return;
		}
		PitchDirection pitch = wp.pitchDirectionToCamera(level, mc.gameRenderer, pts);
		LocatorTracker.Mode mode = LocatorTracker.mode();
		Vec3 known = LocatorTracker.knownPos(camEntity.getY());
		double distSq = known != null ? known.distanceToSqr(camEntity.position()) : wp.distanceSquared(camEntity);

		int color = wp.icon().color.orElseGet(() -> ARGB.setBrightness(ARGB.color(255, LocatorTracker.targetId().hashCode()), 0.9F));
		color = ARGB.opaque(color);

		String dist = Double.isInfinite(distSq) ? "xa" : Math.round(Math.sqrt(distSq)) + "m";
		String vertical = (known == null || mode == LocatorTracker.Mode.CHUNK)
			? (pitch == PitchDirection.UP ? "  ⬆ lên" : pitch == PitchDirection.DOWN ? "  ⬇ xuống" : "") : "";
		if (known == null) mode = LocatorTracker.Mode.DIRECTION;
		String coords = switch (mode) {
			case BLOCK -> "  [" + (int) Math.floor(known.x) + " " + (int) Math.floor(known.y) + " " + (int) Math.floor(known.z) + "]";
			case CHUNK -> "  [x≈" + (int) Math.floor(known.x) + " z≈" + (int) Math.floor(known.z) + " ~chunk]";
			case DIRECTION -> "  [chỉ hướng]";
		};
		graphics.centeredText(mc.font, name + "  " + dist + vertical + coords, cx, y, color);

		String pathStatus = PathPlanner.status();
		if (!pathStatus.isEmpty()) graphics.centeredText(mc.font, pathStatus, cx, y + 22, PathPlanner.reachedGoal() ? 0xFF55FF77 : 0xFFFFAA33);

		String side = Math.abs(angle) < 3 ? "thẳng phía trước"
			: Math.abs(angle) > 177 ? "ngay phía sau"
			: (angle > 0 ? "phải " : "trái ") + Math.round(Math.abs(angle)) + "°";
		graphics.centeredText(mc.font, arrow(angle) + "  " + side, cx, y + 11, 0xFFFFFFFF);
	}

	private static String arrow(double a) {
		double abs = Math.abs(a);
		boolean right = a > 0;
		if (abs < 22.5) return "↑";
		if (abs < 67.5) return right ? "↗" : "↖";
		if (abs < 112.5) return right ? "→" : "←";
		if (abs < 157.5) return right ? "↘" : "↙";
		return "↓";
	}
}
