package khoa.tools.client.feature.camera;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import khoa.tools.KhoaTools;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.util.KeyCategory;
import khoa.tools.setting.Setting;

/**
 * Saved camera points per server + dimension. /khoatoolsclient camera create|changelocation|select|watch|stopwatch|rotateable|list|delete.
 * Watching moves the view to the camera spot (inside render distance only) and freezes the player; the Watch cam key does the same while held.
 */
public final class CameraFeature implements KhoaFeature {
	private static final int SELECTED_COLOR = ARGB.color(255, 255, 220, 40);
	private static final int OTHER_COLOR = ARGB.color(255, 80, 200, 255);

	private static boolean enabled = KhoaConfig.getBool("camera", "enabled", true);
	private static boolean rotateable = KhoaConfig.getBool("camera", "rotateable", false);
	private static String selected;
	private static KeyMapping watchKey;
	private static KeyMapping freecamKey;
	private static boolean freecamWasDown;

	public static boolean rotateable() {
		return rotateable;
	}

	@Override public String id() { return "camera"; }
	@Override public String displayName() { return "Camera"; }
	@Override public String description() {
		return "Saved camera points (per server and dimension). Commands: /khoatoolsclient camera create <name> | changelocation <name> | select <name> | watch <name> | freecam | stopwatch | rotateable [true|false] | list | delete <name>. "
			+ "'create' saves your current camera position and rotation (a marker shows where it is); 'select' only picks it; 'watch' moves your view there (only inside render distance) and freezes your movement; 'stopwatch' returns. "
			+ "Hold the 'Watch cam' key (Controls > KhoaTools) to watch the selected camera and release it to return. Clicking the card turns the whole feature on/off.";
	}

	@Override public boolean isActive() { return enabled; }

	@Override public void activate(Minecraft mc) {
		setEnabled(!enabled);
		KhoaFeature.say(mc, "[Camera] " + (enabled ? "Enabled." : "Disabled."));
	}

	@Override public List<Setting> settings() {
		return List.of(
			Setting.bool("enabled", "Enabled", "Turns the whole camera feature on or off.", () -> enabled, CameraFeature::setEnabled),
			Setting.bool("rotateable", "Rotateable", "While watching a camera you can turn the view with the mouse. Off = the view keeps the rotation saved with the camera.",
				() -> rotateable, v -> {
					rotateable = v;
					KhoaConfig.put("camera", "rotateable", v);
				}));
	}

	private static void setEnabled(boolean value) {
		enabled = value;
		KhoaConfig.put("camera", "enabled", value);
		if (!value) CameraWatch.stop();
	}

	@Override public void onInit() {
		watchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.khoatools.camera_watch", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KeyCategory.get()));
		freecamKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.khoatools.freecam", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KeyCategory.get()));
		ClientTickEvents.END_CLIENT_TICK.register(CameraFeature::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CameraWatch.stop();
			selected = null;
		});
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderMarkers());
		HudElementRegistry.addLast(KhoaTools.id("camera_overlay"), CameraFeature::renderHud);
	}

	private static void tick(Minecraft mc) {
		CameraWatch.validate(mc);
		if (!enabled && CameraWatch.isWatching()) CameraWatch.stop();
		boolean fcDown = freecamKey != null && freecamKey.isDown() && mc.screen == null;
		if (fcDown && !freecamWasDown) toggleFreecam(mc);
		freecamWasDown = fcDown;
		boolean down = watchKey != null && watchKey.isDown() && mc.screen == null;
		if (down && !CameraWatch.isWatching() && enabled && mc.player != null) {
			CameraStore.Cam cam = selected == null ? null : CameraStore.find(mc, selected);
			if (cam == null) {
				KhoaFeature.say(mc, "[Camera] No camera selected here. Use /khoatoolsclient camera select <name>.");
			} else if (!CameraStore.isInRange(mc, new Vec3(cam.x, cam.y, cam.z))) {
				KhoaFeature.say(mc, "[Camera] '" + cam.name + "' is outside your render distance.");
			} else {
				CameraWatch.start(mc, cam, true);
			}
		} else if (!down && CameraWatch.isFromKey()) {
			CameraWatch.stop();
		}
	}

	private static void toggleFreecam(Minecraft mc) {
		if (CameraWatch.isFree()) {
			CameraWatch.stop();
			KhoaFeature.say(mc, "[Camera] Freecam off.");
		} else if (!enabled) {
			KhoaFeature.say(mc, "[Camera] The camera feature is off. Run /khoatoolsclient camera to turn it on.");
		} else if (mc.player != null) {
			CameraWatch.startFree(mc);
			KhoaFeature.say(mc, "[Camera] Freecam on: WASD/jump/sneak to fly, mouse wheel = speed, no block interaction.");
		}
	}

	// ---- rendering ----

	private static void renderMarkers() {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled || mc.level == null) return;
		for (CameraStore.Cam cam : CameraStore.inRange(mc)) {
			if (CameraWatch.isWatching() && cam.name.equals(CameraWatch.name())) continue;
			int color = cam.name.equalsIgnoreCase(selected) ? SELECTED_COLOR : OTHER_COLOR;
			Vec3 p = new Vec3(cam.x, cam.y, cam.z);
			Gizmos.cuboid(new AABB(p.x - 0.2, p.y - 0.15, p.z - 0.2, p.x + 0.2, p.y + 0.15, p.z + 0.2), GizmoStyle.stroke(color, 2.0F)).setAlwaysOnTop();
			Vec3 look = Vec3.directionFromRotation(cam.pitch, cam.yaw).scale(1.2);
			Gizmos.arrow(p, p.add(look), color, 3.0F).setAlwaysOnTop();
			Gizmos.billboardText(cam.name, p.add(0, 0.5, 0), TextGizmo.Style.forColorAndCentered(color));
		}
	}

	private static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!CameraWatch.isWatching() || mc.level == null || mc.options.hideGui) return;
		if (CameraWatch.isFree()) {
			g.text(mc.font, String.format("Freecam  speed %.1f (mouse wheel)", CameraWatch.speed()), 10, 10, 0xFFFFFFFF);
			return;
		}
		long t = mc.level.getOverworldClockTime();
		long day = Math.floorDiv(t, 24000L) + 1;
		long inDay = Math.floorMod(t, 24000L);
		long hour = (inDay / 1000 + 6) % 24;
		long minute = (inDay % 1000) * 60 / 1000;
		String text = String.format("Day %d  %02d:%02d", day, hour, minute);
		int x = 10;
		int y = 10;
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int red = 0xFFFF2020;
			g.fill(x + 1, y, x + 7, y + 8, red);
			g.fill(x, y + 1, x + 8, y + 7, red);
		}
		g.text(mc.font, text, x + 14, y, 0xFFFFFFFF);
		g.text(mc.font, "CAM " + CameraWatch.name(), x + 14, y + 11, 0xFFAAAAAA);
	}

	// ---- commands ----

	@Override public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.executes(ctx -> {
				activate(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("create")
				.then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					if (mc.player == null) return 0;
					Camera cam = mc.gameRenderer.getMainCamera();
					String name = StringArgumentType.getString(ctx, "name");
					return reply(mc, CameraStore.create(mc, name, cam.position(), cam.yRot(), cam.xRot()),
						"Saved camera '" + name + "'. It is not selected yet: use select " + name + " or watch " + name + ".");
				})))
			.then(ClientCommands.literal("changelocation")
				.then(named().executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					if (mc.player == null) return 0;
					if (CameraWatch.isWatching()) return reply(mc, "Stop watching first (stopwatch).", "");
					Camera cam = mc.gameRenderer.getMainCamera();
					String name = StringArgumentType.getString(ctx, "name");
					return reply(mc, CameraStore.move(mc, name, cam.position(), cam.yRot(), cam.xRot()), "Camera '" + name + "' moved to your current position and rotation.");
				})))
			.then(ClientCommands.literal("select")
				.then(named().executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					String name = StringArgumentType.getString(ctx, "name");
					CameraStore.Cam cam = CameraStore.find(mc, name);
					if (cam == null) return reply(mc, "No camera named '" + name + "' in this world.", "");
					selected = cam.name;
					return reply(mc, null, "Selected camera '" + cam.name + "'.");
				})))
			.then(ClientCommands.literal("watch")
				.then(named().executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					String name = StringArgumentType.getString(ctx, "name");
					if (!enabled) return reply(mc, "The camera feature is off. Run /khoatoolsclient camera to turn it on.", "");
					CameraStore.Cam cam = CameraStore.find(mc, name);
					if (cam == null) return reply(mc, "No camera named '" + name + "' in this world.", "");
					if (!CameraStore.isInRange(mc, new Vec3(cam.x, cam.y, cam.z))) return reply(mc, "'" + cam.name + "' is outside your render distance.", "");
					CameraWatch.start(mc, cam, false);
					return reply(mc, null, "Watching '" + cam.name + "'. Movement is frozen; use stopwatch to return.");
				})))
			.then(ClientCommands.literal("freecam").executes(ctx -> {
				toggleFreecam(ctx.getSource().getClient());
				return 1;
			}))
			.then(ClientCommands.literal("stopwatch").executes(ctx -> {
				Minecraft mc = ctx.getSource().getClient();
				if (!CameraWatch.isWatching()) return reply(mc, "You are not watching a camera.", "");
				CameraWatch.stop();
				return reply(mc, null, "Stopped watching.");
			}))
			.then(ClientCommands.literal("rotateable")
				.executes(ctx -> setRotateable(ctx, !rotateable))
				.then(ClientCommands.argument("value", BoolArgumentType.bool()).executes(ctx -> setRotateable(ctx, BoolArgumentType.getBool(ctx, "value")))))
			.then(ClientCommands.literal("list").executes(ctx -> {
				Minecraft mc = ctx.getSource().getClient();
				List<CameraStore.Cam> all = CameraStore.current(mc);
				if (all.isEmpty()) return reply(mc, null, "No cameras in this server/dimension.");
				StringBuilder sb = new StringBuilder();
				for (CameraStore.Cam c : all) {
					if (sb.length() > 0) sb.append(", ");
					sb.append(c.name).append(CameraStore.isInRange(mc, new Vec3(c.x, c.y, c.z)) ? "" : " (out of range)");
				}
				return reply(mc, null, "Cameras: " + sb);
			}))
			.then(ClientCommands.literal("delete")
				.then(named().executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					String name = StringArgumentType.getString(ctx, "name");
					String err = CameraStore.delete(mc, name);
					if (err == null && name.equalsIgnoreCase(selected)) selected = null;
					if (err == null && CameraWatch.isWatching() && name.equalsIgnoreCase(CameraWatch.name())) CameraWatch.stop();
					return reply(mc, err, "Deleted camera '" + name + "'.");
				}))));
	}

	/** {@code <name>} argument that only suggests cameras of this server/dimension inside the render distance. */
	private static RequiredArgumentBuilder<FabricClientCommandSource, String> named() {
		return ClientCommands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> {
			Minecraft mc = Minecraft.getInstance();
			return SharedSuggestionProvider.suggest(CameraStore.inRange(mc).stream().map(c -> c.name).toList(), builder);
		});
	}

	private static int setRotateable(CommandContext<FabricClientCommandSource> ctx, boolean value) {
		rotateable = value;
		KhoaConfig.put("camera", "rotateable", value);
		return reply(ctx.getSource().getClient(), null, "Rotateable is now " + (value ? "ON" : "OFF") + ".");
	}

	private static int reply(Minecraft mc, String error, String ok) {
		KhoaFeature.say(mc, "[Camera] " + (error != null ? error : ok));
		return error == null ? 1 : 0;
	}
}
