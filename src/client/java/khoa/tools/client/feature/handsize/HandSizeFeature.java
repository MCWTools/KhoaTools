package khoa.tools.client.feature.handsize;

import java.util.List;

import net.minecraft.client.Minecraft;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Change Hand Size (client side only): scales your first person hand together with the item it holds
 * (10% - 150%, 100% = vanilla) and can move it with X / Y / Z offsets.
 * The work is done in ItemInHandRendererMixin; other players are not affected.
 */
public final class HandSizeFeature implements KhoaFeature {
	private static final String ID = "handsize";
	private static boolean on;
	private static int percent = 100;
	private static int offX;
	private static int offY;
	private static int offZ;

	/** Offset in blocks, view space: x = right, y = up, z = towards you (slider value / 100). */
	public static float offsetX() {
		return on ? offX / 100.0F : 0.0F;
	}

	public static float offsetY() {
		return on ? offY / 100.0F : 0.0F;
	}

	public static float offsetZ() {
		return on ? offZ / 100.0F : 0.0F;
	}

	/** 1.0 = vanilla. */
	public static float scale() {
		return on ? percent / 100.0F : 1.0F;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Change Hand Size";
	}

	@Override
	public String description() {
		return "Client side only. Click the card to turn it on/off. Changes the size of your hand and the item in it (first person) "
			+ "from 10% to 150% (100% is normal), and moves it with the X / Y / Z offsets.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.slider("size", "Hand size %", "Normal = 100.", 10, 150, () -> percent, value -> {
				percent = Math.max(10, Math.min(150, value));
				KhoaConfig.put(ID, "size", percent);
			}),
			Setting.slider("x", "Offset X", "Left (-) / right (+). 0 = normal.", -100, 100, () -> offX, value -> {
				offX = value;
				KhoaConfig.put(ID, "x", offX);
			}),
			Setting.slider("y", "Offset Y", "Down (-) / up (+). 0 = normal.", -100, 100, () -> offY, value -> {
				offY = value;
				KhoaConfig.put(ID, "y", offY);
			}),
			Setting.slider("z", "Offset Z", "Away from you (-) / closer (+). 0 = normal.", -100, 100, () -> offZ, value -> {
				offZ = value;
				KhoaConfig.put(ID, "z", offZ);
			}));
	}

	@Override
	public boolean persistEnabled() {
		return true;
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		KhoaFeature.say(mc, "Change Hand Size: " + (on ? "on (" + percent + "%)" : "off"));
	}

	@Override
	public void onInit() {
		percent = Math.max(10, Math.min(150, KhoaConfig.getInt(ID, "size", 100)));
		offX = Math.max(-100, Math.min(100, KhoaConfig.getInt(ID, "x", 0)));
		offY = Math.max(-100, Math.min(100, KhoaConfig.getInt(ID, "y", 0)));
		offZ = Math.max(-100, Math.min(100, KhoaConfig.getInt(ID, "z", 0)));
	}
}
