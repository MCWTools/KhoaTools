package khoa.tools.client.config;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

import khoa.tools.KhoaTools;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.KhoaFeatures;

/**
 * Optional hotkey per feature (config/khoatools.json -> <id>.keybind = GLFW key code, -1 = None).
 * Pressing the key calls {@link KhoaFeature#activate} (same as clicking the card). Only fires with no screen open.
 */
public final class KhoaKeybinds {
	public static final int NONE = -1;

	private static final Map<String, Integer> KEYS = new HashMap<>();
	private static final Map<String, Boolean> WAS_DOWN = new HashMap<>();

	private KhoaKeybinds() {
	}

	public static int get(String featureId) {
		return KEYS.computeIfAbsent(featureId, id -> KhoaConfig.getInt(id, "keybind", NONE));
	}

	public static void set(String featureId, int keyCode) {
		KEYS.put(featureId, keyCode);
		KhoaConfig.put(featureId, "keybind", keyCode);
	}

	/** Text of the keybind button without the brackets, e.g. "None" or "R". */
	public static String keyName(int keyCode) {
		if (keyCode == NONE) return "None";
		return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
	}

	public static void tick(Minecraft mc) {
		if (mc.getWindow() == null) return;
		boolean canFire = mc.screen == null && mc.player != null && mc.isWindowActive();
		for (KhoaFeature feature : KhoaFeatures.all()) {
			int key = get(feature.id());
			if (key == NONE) continue;
			boolean down = InputConstants.isKeyDown(mc.getWindow(), key);
			boolean was = WAS_DOWN.getOrDefault(feature.id(), false);
			WAS_DOWN.put(feature.id(), down);
			if (down && !was && canFire) {
				try {
					feature.activate(mc);
				} catch (RuntimeException e) {
					KhoaTools.LOGGER.error("Feature {} failed from keybind", feature.id(), e);
				}
			}
		}
	}
}
