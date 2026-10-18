package khoa.locator.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;

import java.util.UUID;

/** /playerindirection: lấy yaw đang nhìn, tìm player trên locator bar có hướng gần yaw đó nhất, báo vào chat client (không gửi server). */
public final class PlayerInDirection {
	private PlayerInDirection() {}

	/** @return nội dung để in vào chat client */
	public static Component run(Minecraft mc) {
		Entity camEntity = mc.getCameraEntity();
		if (mc.player == null || mc.level == null || mc.getConnection() == null || camEntity == null)
			return Component.literal("Chưa vào world.");

		Level level = camEntity.level();
		Camera camera = mc.gameRenderer.getMainCamera();
		float lookYaw = Mth.wrapDegrees(mc.player.getYRot());

		UUID[] bestId = new UUID[1];
		TrackedWaypoint[] bestWp = new TrackedWaypoint[1];
		double[] bestDiff = {Double.MAX_VALUE}; // góc lệch so với hướng nhìn (có dấu, + = bên phải)

		mc.getConnection().getWaypointManager().forEachWaypoint(camEntity, wp -> {
			UUID id = wp.id().left().orElse(null);
			if (id == null) return;
			double angleToCamera = wp.yawAngleToCamera(level, camera, e -> 1.0F); // so với hướng camera
			if (Double.isNaN(angleToCamera)) return;
			// đổi sang so với yaw của player (khác camera khi ở góc nhìn thứ ba phía trước)
			double diff = Mth.wrapDegrees(angleToCamera + camera.yaw() - lookYaw);
			if (Math.abs(diff) < Math.abs(bestDiff[0])) {
				bestDiff[0] = diff;
				bestId[0] = id;
				bestWp[0] = wp;
			}
		});

		if (bestId[0] == null) return Component.literal("Không có player nào đang hiện trên locator bar.");

		String name = null;
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			if (info.getProfile().id().equals(bestId[0])) { name = info.getProfile().name(); break; }
		}
		if (name == null) name = bestId[0].toString();

		double d2 = bestWp[0].distanceSquared(camEntity);
		String dist = Double.isInfinite(d2) ? "xa" : Math.round(Math.sqrt(d2)) + "m";
		String kind = switch (bestWp[0].getClass().getSimpleName()) {
			case "Vec3iWaypoint" -> "tọa độ block";
			case "ChunkWaypoint" -> "~chunk";
			default -> "chỉ hướng";
		};
		double diff = bestDiff[0];
		String side = Math.abs(diff) < 1 ? "đúng hướng" : (diff > 0 ? "lệch phải " : "lệch trái ") + String.format("%.1f", Math.abs(diff)) + "°";

		return Component.literal(String.format("Yaw %.1f° → gần nhất: %s (%s, %s, %s)", lookYaw, name, side, dist, kind));
	}
}
