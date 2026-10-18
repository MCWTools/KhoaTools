package khoa.tools.client.feature.afkalert;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.afk.AfkModeFeature;
import khoa.tools.setting.Setting;

/**
 * Two alerts while you are AFK:
 * - Player alert (player volume, loops): another player is within the set distance.
 * - Health alert (health volume, loops): your health is below the set value and nobody is close.
 * "Alert only if health was low and player nearby" (ticked by default): alert only when both are true (player alert).
 * Unticked: either condition alerts on its own. The player alert wins when both apply.
 */
public final class AfkAlertFeature implements KhoaFeature {
	private static final String ID = "afkalert";
	private static final int NONE = 0;
	private static final int HEALTH = 1;
	private static final int PLAYER = 2;

	private static boolean on;
	private static int mode = NONE;
	private static int modeTicks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "AFK Alert";
	}

	@Override
	public String description() {
		return "Alerts while AFK: player alert (own volume) when another player is within the set distance, health alert (own volume) when your health is low. "
			+ "Ticked box = alert only when health is low AND a player is nearby. Unticked = either one alerts on its own. "
			+ "By default it only works while AFK Mode is on.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.slider("distance", "Player distance", "Another player must be this close (blocks) to count as nearby.", 4, 32,
				() -> KhoaConfig.getInt(ID, "distance", 16), v -> KhoaConfig.put(ID, "distance", v)),
			Setting.slider("volume", "Player alert volume", "Volume of the player alert (0-100%). Used whenever a player is nearby.", 0, 100,
				() -> KhoaConfig.getInt(ID, "volume", 100), v -> KhoaConfig.put(ID, "volume", v)),
			Setting.slider("pitch", "Player alert pitch", "Pitch of the player alert (50-200%, 100 = normal).", 50, 200,
				() -> KhoaConfig.getInt(ID, "pitch", 100), v -> KhoaConfig.put(ID, "pitch", v)),
			Setting.slider("health", "Health below", "Alert when your health is below this (HP, 2 HP = 1 heart).", 1, 20,
				() -> KhoaConfig.getInt(ID, "health", 10), v -> KhoaConfig.put(ID, "health", v)),
			Setting.slider("healthvolume", "Health alert volume", "Volume of the health alert (0-100%). Used when health is low and nobody is nearby.", 0, 100,
				() -> KhoaConfig.getInt(ID, "healthvolume", 30), v -> KhoaConfig.put(ID, "healthvolume", v)),
			Setting.slider("healthpitch", "Health alert pitch", "Pitch of the health alert (50-200%, 100 = normal).", 50, 200,
				() -> KhoaConfig.getInt(ID, "healthpitch", 80), v -> KhoaConfig.put(ID, "healthpitch", v)),
			Setting.bool("onlylow", "Alert only if health was low and player nearby", "Ticked: alert only when your health is below \"Health below\" AND a player is nearby. Unticked: a nearby player alerts at any health, and low health alerts even with nobody close.",
				() -> KhoaConfig.getBool(ID, "onlylow", true), v -> KhoaConfig.put(ID, "onlylow", v)),
			Setting.bool("afkonly", "Only while AFK Mode is on", "Untick to watch all the time.",
				() -> KhoaConfig.getBool(ID, "afkonly", true), v -> KhoaConfig.put(ID, "afkonly", v)),
			Setting.action("testplayer", "Test player alert", "Plays the player alert sound once (horn + bell).", "Play", () -> testPlayer(Minecraft.getInstance())),
			Setting.action("testhealth", "Test health alert", "Plays the health alert sound once.", "Play", () -> testHealth(Minecraft.getInstance())));
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
		mode = NONE;
		KhoaFeature.say(mc, "AFK Alert: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(AfkAlertFeature::tick);
	}

	private static float volume(String key, int fallback) {
		return Math.max(0, Math.min(100, KhoaConfig.getInt(ID, key, fallback))) / 100.0f;
	}

	private static float pitch(String key, int fallback, float factor) {
		return Math.max(0.5f, Math.min(2.0f, KhoaConfig.getInt(ID, key, fallback) / 100.0f * factor));
	}

	private static void play(Minecraft mc, SoundEvent sound, float pitch, float volume) {
		if (volume <= 0.0f) {
			return;
		}
		// Played on the main thread; plain UI sound (no distance, no direction) so it is always heard at full volume.
		mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume)));
	}

	/** Player alert: horn + bell, both at the player volume and pitch. */
	private static void playLoud(Minecraft mc) {
		float volume = volume("volume", 100);
		play(mc, SoundEvents.RAID_HORN.value(), pitch("pitch", 100, 1.0f), volume);
		play(mc, SoundEvents.BELL_BLOCK, pitch("pitch", 100, 0.7f), volume);
	}

	/** Health alert: a pling at the health volume and pitch. */
	private static void playQuiet(Minecraft mc) {
		play(mc, SoundEvents.NOTE_BLOCK_PLING.value(), pitch("healthpitch", 80, 1.0f), volume("healthvolume", 30));
	}

	private static void testPlayer(Minecraft mc) {
		if (volume("volume", 100) <= 0.0f) {
			KhoaFeature.say(mc, "AFK Alert: Player alert volume is 0 - raise the slider to hear it");
			return;
		}
		playLoud(mc);
	}

	private static void testHealth(Minecraft mc) {
		if (volume("healthvolume", 30) <= 0.0f) {
			KhoaFeature.say(mc, "AFK Alert: Health alert volume is 0 - raise the slider to hear it");
			return;
		}
		playQuiet(mc);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer me = mc.player;
		ClientLevel level = mc.level;
		if (!on || me == null || level == null || mc.getConnection() == null
			|| (KhoaConfig.getBool(ID, "afkonly", true) && !AfkModeFeature.isOn()) || !me.isAlive()) {
			mode = NONE;
			return;
		}

		boolean low = me.getHealth() < KhoaConfig.getInt(ID, "health", 10);
		float maxDistance = KhoaConfig.getInt(ID, "distance", 16);
		AbstractClientPlayer nearest = null;
		float nearestDistance = Float.MAX_VALUE;
		for (AbstractClientPlayer other : level.players()) {
			if (other == me || other.isSpectator() || mc.getConnection().getPlayerInfo(other.getUUID()) == null) {
				continue; // yourself, spectators, and fake players (NPCs are not in the tab list)
			}
			float distance = me.distanceTo(other);
			if (distance <= maxDistance && distance < nearestDistance) {
				nearest = other;
				nearestDistance = distance;
			}
		}

		boolean onlyBoth = KhoaConfig.getBool(ID, "onlylow", true);
		int wanted;
		if (onlyBoth) {
			// Ticked: alert only when health is low AND a player is nearby (player volume).
			wanted = (low && nearest != null) ? PLAYER : NONE;
		} else {
			// Unticked: a nearby player always alerts (player volume); low health alone alerts too (health volume).
			wanted = nearest != null ? PLAYER : (low ? HEALTH : NONE);
		}
		if (wanted != mode) {
			mode = wanted;
			modeTicks = 0;
			if (mode == PLAYER) {
				KhoaFeature.say(mc, "AFK ALERT: " + nearest.getName().getString() + " is " + Math.round(nearestDistance)
					+ " blocks away - your HP: " + Math.round(me.getHealth()));
			} else if (mode == HEALTH) {
				KhoaFeature.say(mc, "AFK Alert: low health - your HP: " + Math.round(me.getHealth()));
			}
		}

		if (mode == PLAYER) {
			if (modeTicks % 40 == 0) {
				mc.gui.setTimes(0, 50, 10);
				mc.gui.setTitle(Component.literal("AFK ALERT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
				mc.gui.setSubtitle(Component.literal(nearest.getName().getString() + " is " + Math.round(nearestDistance)
					+ " blocks away - HP " + Math.round(me.getHealth())).withStyle(ChatFormatting.YELLOW));
				playLoud(mc);
			}
			if (modeTicks % 8 == 0) {
				float factor = (modeTicks / 8) % 2 == 0 ? 0.7f : 1.5f;
				play(mc, SoundEvents.BELL_BLOCK, pitch("pitch", 100, factor), volume("volume", 100));
			}
		} else if (mode == HEALTH && modeTicks % 20 == 0) {
			playQuiet(mc);
			mc.gui.setOverlayMessage(Component.literal("Low health: " + Math.round(me.getHealth()) + " HP").withStyle(ChatFormatting.RED), false);
		}
		modeTicks++;
	}
}
