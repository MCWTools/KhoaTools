package khoa.locator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;

import java.lang.reflect.Field;
import java.util.UUID;

/** Giữ target + waypoint tương ứng (tìm lại mỗi 5 tick) và đọc xem server gửi coord / chunk / hướng. */
public final class LocatorTracker {
	/** BLOCK = tọa độ block chính xác, CHUNK = chỉ chunk X/Z, DIRECTION = chỉ góc ngang. */
	public enum Mode { BLOCK, CHUNK, DIRECTION }

	private static volatile UUID targetId;
	private static volatile String targetName;
	private static volatile TrackedWaypoint waypoint;
	private static volatile Mode mode = Mode.DIRECTION;
	private static volatile Vec3 fixedPos; // BLOCK: tâm block; CHUNK: tâm chunk (y = NaN)

	// Field private trong class private static của vanilla -> đọc bằng reflection (fail thì rơi về DIRECTION)
	private static final Field VECTOR = field("net.minecraft.world.waypoints.TrackedWaypoint$Vec3iWaypoint", "vector");
	private static final Field CHUNK_POS = field("net.minecraft.world.waypoints.TrackedWaypoint$ChunkWaypoint", "chunkPos");

	private LocatorTracker() {}

	private static Field field(String className, String name) {
		try {
			Field f = Class.forName(className).getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable t) {
			return null;
		}
	}

	public static void setTarget(UUID id, String name) {
		targetId = id;
		targetName = name;
		waypoint = null;
		mode = Mode.DIRECTION;
		fixedPos = null;
		refresh(Minecraft.getInstance());
	}

	public static void clear() {
		targetId = null;
		targetName = null;
		waypoint = null;
		mode = Mode.DIRECTION;
		fixedPos = null;
	}

	public static boolean active() { return targetId != null; }
	public static UUID targetId() { return targetId; }
	public static String targetName() { return targetName; }
	public static TrackedWaypoint waypoint() { return waypoint; }
	public static Mode mode() { return mode; }

	/** Vị trí đã biết của target, hoặc null nếu server chỉ gửi hướng. CHUNK thì Y lấy theo camY. */
	public static Vec3 knownPos(double camY) {
		Vec3 p = fixedPos;
		if (p == null) return null;
		return mode == Mode.CHUNK ? new Vec3(p.x, camY, p.z) : p;
	}

	public static void refresh(Minecraft client) {
		UUID id = targetId;
		if (id == null || client.player == null || client.player.connection == null) return;
		Entity camera = client.getCameraEntity();
		if (camera == null) return;
		TrackedWaypoint[] found = new TrackedWaypoint[1];
		client.player.connection.getWaypointManager().forEachWaypoint(camera, wp -> {
			if (wp.id().left().map(id::equals).orElse(false)) found[0] = wp;
		});
		waypoint = found[0];
		classify(found[0]);
	}

	private static void classify(TrackedWaypoint wp) {
		Mode m = Mode.DIRECTION;
		Vec3 p = null;
		if (wp != null) {
			try {
				String n = wp.getClass().getSimpleName();
				if (n.equals("Vec3iWaypoint") && VECTOR != null) {
					p = Vec3.atCenterOf((Vec3i) VECTOR.get(wp));
					m = Mode.BLOCK;
				} else if (n.equals("ChunkWaypoint") && CHUNK_POS != null) {
					BlockPos bp = ((ChunkPos) CHUNK_POS.get(wp)).getMiddleBlockPosition(0);
					p = new Vec3(bp.getX() + 0.5, Double.NaN, bp.getZ() + 0.5);
					m = Mode.CHUNK;
				}
			} catch (Throwable ignored) {
				m = Mode.DIRECTION;
				p = null;
			}
		}
		mode = m;
		fixedPos = p;
	}
}
