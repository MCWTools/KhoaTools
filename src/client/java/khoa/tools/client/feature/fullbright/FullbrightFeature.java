package khoa.tools.client.feature.fullbright;

import net.minecraft.client.Minecraft;

import khoa.tools.client.feature.KhoaFeature;

/**
 * Makes the lightmap fully bright (same trick as a night vision potion at full strength, without the potion).
 * The work is done in LightmapRenderStateExtractorMixin; the gamma option is not touched, so nothing to restore.
 */
public final class FullbrightFeature implements KhoaFeature {
	private static boolean on;

	public static boolean enabled() {
		return on;
	}

	public static void setEnabled(boolean enabled) {
		on = enabled;
	}

	@Override
	public String id() {
		return "fullbright";
	}

	@Override
	public String displayName() {
		return "Fullbright";
	}

	@Override
	public String description() {
		return "Everything is fully lit, like permanent night vision. Click the card to turn it on/off. No settings.";
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
		setEnabled(!on);
		KhoaFeature.say(mc, "Fullbright: " + (on ? "on" : "off"));
	}
}
