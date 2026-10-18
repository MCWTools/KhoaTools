package khoa.tools.client.feature.afk;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.minecraft.client.Minecraft;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.util.OptionUtil;
import khoa.tools.setting.Setting;

/**
 * A preset of vanilla video options (so Sodium and others see the same values): lower FPS limit, render distance and
 * simulation distance. Your real values are saved when it turns on and restored when it turns off.
 */
public final class AfkModeFeature implements KhoaFeature {
	private static boolean on;
	private static int oldFps, oldRender, oldSim;

	@Override
	public String id() {
		return "afkmode";
	}

	@Override
	public String displayName() {
		return "AFK Mode";
	}

	@Override
	public String description() {
		return "Lowers FPS limit, render distance and simulation distance to save your PC while AFK. Turning it off restores your old values. "
			+ "Simulation distance only matters in singleplayer.";
	}

	@Override
	public List<Setting> settings() {
		String id = id();
		return List.of(
			Setting.slider("fps", "FPS Limit", "Max FPS while AFK mode is on.", 5, 30,
				() -> KhoaConfig.getInt(id, "fps", 10), v -> changed("fps", v)),
			Setting.slider("render", "Render Distance", "Render distance (chunks) while AFK mode is on.", 2, 8,
				() -> KhoaConfig.getInt(id, "render", 4), v -> changed("render", v)),
			Setting.slider("sim", "Simulation Distance", "Simulation distance (chunks) while AFK mode is on.", 2, 16,
				() -> KhoaConfig.getInt(id, "sim", 5), v -> changed("sim", v)));
	}

	private static void changed(String key, int value) {
		KhoaConfig.put("afkmode", key, value);
		if (on) {
			set(Minecraft.getInstance(), cfg("fps", 10), cfg("render", 4), cfg("sim", 5));
		}
	}

	private static int cfg(String key, int fallback) {
		return KhoaConfig.getInt("afkmode", key, fallback);
	}

	/** Render distance goes through the normal setter (it triggers a chunk refresh); fps and sim need force() because they go below the vanilla minimum. */
	private static void set(Minecraft mc, int fps, int render, int sim) {
		OptionUtil.force(mc.options.framerateLimit(), fps);
		mc.getFramerateLimitTracker().setFramerateLimit(fps);
		mc.options.renderDistance().set(render);
		OptionUtil.force(mc.options.simulationDistance(), sim);
	}

	/** True while AFK Mode is on (used by AFK Alert). */
	public static boolean isOn() {
		return on;
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		if (on) {
			oldFps = mc.options.framerateLimit().get();
			oldRender = mc.options.renderDistance().get();
			oldSim = mc.options.simulationDistance().get();
			set(mc, cfg("fps", 10), cfg("render", 4), cfg("sim", 5));
		} else {
			set(mc, oldFps, oldRender, oldSim);
		}
		KhoaFeature.say(mc, "AFK Mode: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			if (on) {
				set(mc, oldFps, oldRender, oldSim);
			}
		});
	}
}
