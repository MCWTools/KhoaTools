package khoa.tools.client.feature.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Runtime state of "watching" a saved camera: the view is detached to the camera's spot while the player stays frozen. */
public final class CameraWatch {
	private static boolean watching;
	private static boolean fromKey;
	private static String name = "";
	private static String worldKey;
	private static Vec3 pos = Vec3.ZERO;
	private static float yaw;
	private static float pitch;
	private static boolean free;
	private static double speed = 10.0;
	private static long lastNanos;

	private CameraWatch() {
	}

	public static boolean isWatching() {
		return watching;
	}

	public static boolean isFromKey() {
		return watching && fromKey;
	}

	public static String name() {
		return name;
	}

	public static Vec3 pos() {
		return pos;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static void start(Minecraft mc, CameraStore.Cam cam, boolean viaKey) {
		free = false;
		watching = true;
		fromKey = viaKey;
		name = cam.name;
		worldKey = CameraStore.worldKey(mc);
		pos = new Vec3(cam.x, cam.y, cam.z);
		yaw = cam.yaw;
		pitch = cam.pitch;
	}

	/** Freecam: the view starts at the current camera and flies with WASD / jump / sneak; the player stays frozen. */
	public static void startFree(Minecraft mc) {
		net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
		watching = true;
		fromKey = false;
		free = true;
		name = "Freecam";
		worldKey = CameraStore.worldKey(mc);
		pos = cam.position();
		yaw = cam.yRot();
		pitch = cam.xRot();
		lastNanos = System.nanoTime();
	}

	public static boolean isFree() {
		return watching && free;
	}

	public static double speed() {
		return speed;
	}

	/** Mouse wheel while in freecam: faster / slower. */
	public static void scroll(double amount) {
		speed = Math.max(1.0, Math.min(100.0, speed * Math.pow(1.15, amount)));
	}

	/** Once per rendered frame: moves the freecam by the held movement keys, kept inside the render distance of the player. */
	public static void frame(Minecraft mc) {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastNanos) / 1.0e9);
		lastNanos = now;
		if (!isFree() || mc.player == null || mc.level == null || mc.screen != null) return;
		double forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		double side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		double up = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
		double yawRad = Math.toRadians(yaw);
		double fx = -Math.sin(yawRad);
		double fz = Math.cos(yawRad);
		double dx = fx * forward + fz * side;
		double dz = fz * forward - fx * side;
		double step = speed * dt;
		double max = Math.max(1, mc.options.getEffectiveRenderDistance() - 1) * 16.0;
		double nx = Math.max(mc.player.getX() - max, Math.min(mc.player.getX() + max, pos.x + dx * step));
		double nz = Math.max(mc.player.getZ() - max, Math.min(mc.player.getZ() + max, pos.z + dz * step));
		double ny = Math.max(mc.level.getMinY(), Math.min(mc.level.getMaxY(), pos.y + up * step));
		pos = new Vec3(nx, ny, nz);
	}

	public static void stop() {
		watching = false;
		fromKey = false;
		free = false;
	}

	/** Called every tick: ends the watch when the world / dimension changed. */
	public static void validate(Minecraft mc) {
		if (watching && (mc.level == null || !java.util.Objects.equals(worldKey, CameraStore.worldKey(mc)))) {
			stop();
		}
	}

	/** Mouse movement while watching (same scale as {@code Entity.turn}). Ignored unless rotateable is on. */
	public static void turn(double dYaw, double dPitch) {
		if (!free && !CameraFeature.rotateable()) return;
		yaw += (float) dYaw * 0.15F;
		pitch = Math.max(-90.0F, Math.min(90.0F, pitch + (float) dPitch * 0.15F));
	}
}
