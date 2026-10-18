package khoa.tools.client.feature.sneak;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import khoa.tools.client.feature.KhoaFeature;

/** Holds the sneak key down for you while it is on. Click the card again (or /khoatoolsclient sneak) to let go. */
public final class SneakFeature implements KhoaFeature {
	private static boolean on;

	@Override
	public String id() {
		return "sneak";
	}

	@Override
	public String displayName() {
		return "Sneak";
	}

	@Override
	public String description() {
		return "Keeps you sneaking without holding the key. Click the card again to stop.";
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		if (!on) {
			release(mc);
		}
		KhoaFeature.say(mc, "Sneak: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.START_CLIENT_TICK.register(SneakFeature::tick);
	}

	private static void tick(Minecraft mc) {
		if (!on || mc.player == null) {
			return;
		}
		KeyMapping key = mc.options.keyShift;
		// only press when it is up: with the "toggle sneak" option, pressing again would switch it off
		if (!key.isDown()) {
			key.setDown(true);
		}
	}

	private static void release(Minecraft mc) {
		KeyMapping key = mc.options.keyShift;
		if (key.isDown()) {
			key.setDown(false);
			if (key.isDown()) {
				key.setDown(true); // "toggle sneak" ignores setDown(false) and flips on setDown(true)
			}
		}
	}
}
