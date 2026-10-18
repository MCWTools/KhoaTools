package khoa.tools.client.feature.kickmyself;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/** One-shot (pulse): disconnects you from the current server/world and shows your own text as the kick message. */
public final class KickMyselfFeature implements KhoaFeature {
	@Override
	public String id() {
		return "kickmyself";
	}

	@Override
	public String displayName() {
		return "Kick Myself";
	}

	@Override
	public String description() {
		return "Click the card and you kick yourself with the message below (one-shot, nothing stays on). "
			+ "Color codes: write & then 0-9 a-f (colors) or k-o, r (bold, italic, reset). Enter = new line.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.text("message", "Kick message", "Shown on the disconnect screen. Example: &cYou were kicked&7 for being cool.",
			() -> KhoaConfig.getString(id(), "message", "&cDisconnected\n&7Reason: you kicked yourself"), v -> KhoaConfig.put(id(), "message", v)));
	}

	@Override
	public void activate(Minecraft mc) {
		if (mc.getConnection() == null) {
			return;
		}
		String raw = KhoaConfig.getString(id(), "message", "&cDisconnected\n&7Reason: you kicked yourself");
		String text = raw.replaceAll("&(?=[0-9a-fk-orA-FK-OR])", "\u00a7");
		mc.getConnection().getConnection().disconnect(Component.literal(text));
	}
}
