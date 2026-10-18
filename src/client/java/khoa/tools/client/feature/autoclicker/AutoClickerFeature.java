package khoa.tools.client.feature.autoclicker;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.mixin.KeyMappingAccessor;
import khoa.tools.client.util.Ticks;
import khoa.tools.setting.Setting;

/**
 * Clicks Attack or Use on a timer. Stays on after you leave/rejoin a server; pauses while a screen is open.
 * Hold time 0 = single click. With a hold time, the key is held first and the interval only starts counting after the release.
 */
public final class AutoClickerFeature implements KhoaFeature {
	private enum Phase {
		WAIT, HOLD
	}

	private static boolean on;
	private static Phase phase = Phase.WAIT;
	private static int counter;

	@Override
	public String id() {
		return "autoclicker";
	}

	@Override
	public String displayName() {
		return "Auto Clicker";
	}

	@Override
	public String description() {
		return "Automatically clicks Attack or Use (farming, mob grinders). Click the card to turn it on/off; it stays on when you rejoin. "
			+ "Pauses while a menu is open. Hold time 0 = one click per interval.";
	}

	@Override
	public List<Setting> settings() {
		String id = id();
		return List.of(
			Setting.choice("mode", "Mode", "Attack = left click, Use = right click.", List.of("Attack", "Use"),
				() -> KhoaConfig.getInt(id, "mode", 0), v -> {
					release(Minecraft.getInstance());
					KhoaConfig.put(id, "mode", v);
				}),
			Setting.decimal("interval", "Interval (seconds)", "Time between clicks. Converted to ticks: seconds x 20, rounded up (min 1 tick).",
				0.05, 3600, () -> KhoaConfig.getDouble(id, "interval", 0.5), v -> KhoaConfig.putDouble(id, "interval", v)),
			Setting.decimal("hold", "Hold time (seconds)", "How long the button is held per click. 0 = a single click. The interval starts after the hold ends.",
				0, 3600, () -> KhoaConfig.getDouble(id, "hold", 0), v -> KhoaConfig.putDouble(id, "hold", v)),
			Setting.integer("lock_slot", "Lock hotbar slot", "Keeps this hotbar slot selected while the clicker is on. 0 = disabled.",
				0, 9, () -> KhoaConfig.getInt(id, "lock_slot", 0), v -> KhoaConfig.put(id, "lock_slot", v)),
			Setting.bool("lock_look", "Lock look angle", "Forces the angle below every tick while the clicker is on.",
				() -> KhoaConfig.getBool(id, "lock_look", false), v -> KhoaConfig.put(id, "lock_look", v)),
			Setting.pair("look_angle", "Look angle", "Angle used by Lock look angle. Yaw is wrapped to -180..180, pitch is limited to -90..90.",
				"Yaw", () -> KhoaConfig.getDouble(id, "yaw", 0), v -> KhoaConfig.putDouble(id, "yaw", Mth.wrapDegrees(v)),
				"Pitch", () -> KhoaConfig.getDouble(id, "pitch", 0), v -> KhoaConfig.putDouble(id, "pitch", Mth.clamp(v, -90.0, 90.0))));
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		release(mc);
		counter = 0; // first click on the next tick
		KhoaFeature.say(mc, "Auto Clicker: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.START_CLIENT_TICK.register(AutoClickerFeature::tick);
	}

	private static KeyMapping key(Minecraft mc) {
		return KhoaConfig.getInt("autoclicker", "mode", 0) == 0 ? mc.options.keyAttack : mc.options.keyUse;
	}

	private static void release(Minecraft mc) {
		if (phase == Phase.HOLD) {
			mc.options.keyAttack.setDown(false);
			mc.options.keyUse.setDown(false);
		}
		phase = Phase.WAIT;
	}

	private static void click(KeyMapping key) {
		KeyMappingAccessor acc = (KeyMappingAccessor) key;
		acc.khoa$setClickCount(acc.khoa$getClickCount() + 1);
	}

	private static void tick(Minecraft mc) {
		if (!on) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.screen != null) {
			release(mc);
			return;
		}

		int slot = KhoaConfig.getInt("autoclicker", "lock_slot", 0);
		if (slot >= 1 && slot <= 9 && player.getInventory().getSelectedSlot() != slot - 1) {
			player.getInventory().setSelectedSlot(slot - 1);
		}
		if (KhoaConfig.getBool("autoclicker", "lock_look", false)) {
			float yaw = Mth.wrapDegrees((float) KhoaConfig.getDouble("autoclicker", "yaw", 0));
			float pitch = Mth.clamp((float) KhoaConfig.getDouble("autoclicker", "pitch", 0), -90f, 90f);
			player.setYRot(yaw);
			player.setXRot(pitch);
			player.yRotO = yaw;
			player.xRotO = pitch;
		}

		KeyMapping key = key(mc);
		int interval = Math.max(1, Ticks.ofSeconds(KhoaConfig.getDouble("autoclicker", "interval", 0.5)));
		if (phase == Phase.HOLD) {
			key.setDown(true);
			if (--counter <= 0) {
				key.setDown(false);
				phase = Phase.WAIT;
				counter = interval; // the interval starts only now, after the hold
			}
			return;
		}
		if (--counter > 0) {
			return;
		}
		int hold = Ticks.ofSeconds(KhoaConfig.getDouble("autoclicker", "hold", 0));
		click(key);
		if (hold == 0) {
			counter = interval;
		} else {
			key.setDown(true);
			phase = Phase.HOLD;
			counter = hold;
		}
	}
}
