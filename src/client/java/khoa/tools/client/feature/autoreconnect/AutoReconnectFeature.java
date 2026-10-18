package khoa.tools.client.feature.autoreconnect;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.util.Ticks;
import khoa.tools.setting.Setting;

/** Reconnects to the last multiplayer server after a kick/connection loss (only from the "Disconnected" screen). */
public final class AutoReconnectFeature implements KhoaFeature {
	private static boolean on;
	private static ServerData last;
	private static int wait = -1;

	@Override
	public String id() {
		return "autoreconnect";
	}

	@Override
	public String displayName() {
		return "Auto Reconnect";
	}

	@Override
	public String description() {
		return "When you get disconnected from a server, joins it again after the interval. Keeps retrying while the server is down. "
			+ "Leaving with the Disconnect button does not trigger it.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.decimal("interval", "Interval (seconds)", "Wait before each reconnect attempt. Converted to ticks: seconds x 20, rounded up.",
			0.05, 3600, () -> KhoaConfig.getDouble(id(), "interval", 5), v -> KhoaConfig.putDouble(id(), "interval", v)));
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		wait = -1;
		KhoaFeature.say(mc, "Auto Reconnect: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != null && mc.getCurrentServer() != null) {
				last = mc.getCurrentServer(); // remembered even while the feature is off
				wait = -1;
				return;
			}
			if (!on || last == null || !(mc.screen instanceof DisconnectedScreen)) {
				wait = -1;
				return;
			}
			if (wait < 0) {
				wait = Math.max(1, Ticks.ofSeconds(KhoaConfig.getDouble(id(), "interval", 5)));
			}
			if (--wait <= 0) {
				wait = -1;
				ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(last.ip), last, false, null);
			}
		});
	}
}
