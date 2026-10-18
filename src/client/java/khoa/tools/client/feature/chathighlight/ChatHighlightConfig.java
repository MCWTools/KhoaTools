package khoa.tools.client.feature.chathighlight;

import java.util.List;

import net.minecraft.ChatFormatting;

import khoa.tools.client.config.KhoaConfig;

public final class ChatHighlightConfig {
	public static final String SECTION = "chathighlight";
	/** Order matters: the stored value is the index into COLORS. */
	public static final List<String> COLOR_NAMES = List.of("Gold", "Yellow", "Aqua", "Green", "Light Purple", "Red", "White");
	private static final ChatFormatting[] COLORS = {ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.AQUA,
		ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE, ChatFormatting.RED, ChatFormatting.WHITE};

	private ChatHighlightConfig() {
	}

	public static ChatFormatting colorAt(int index) {
		return COLORS[Math.floorMod(index, COLORS.length)];
	}

	public static int color() {
		return Math.floorMod(KhoaConfig.getInt(SECTION, "color", 0), COLORS.length);
	}

	public static void setColor(int index) {
		KhoaConfig.put(SECTION, "color", index);
	}

	public static ChatFormatting colorFormat() {
		return COLORS[color()];
	}

	public static boolean bold() {
		return KhoaConfig.getBool(SECTION, "bold", true);
	}

	public static void setBold(boolean value) {
		KhoaConfig.put(SECTION, "bold", value);
	}

	/** Extra words that count as a mention of you, comma separated (nicknames). */
	public static String keywords() {
		return KhoaConfig.getString(SECTION, "keywords", "");
	}

	public static void setKeywords(String value) {
		KhoaConfig.put(SECTION, "keywords", value);
	}

	/** Also color your name in your own messages (handy to test the feature). */
	public static boolean own() {
		return KhoaConfig.getBool(SECTION, "own", false);
	}

	public static void setOwn(boolean value) {
		KhoaConfig.put(SECTION, "own", value);
	}

	public static float soundVolume() { return (float) KhoaConfig.getDouble(SECTION, "sound_volume", 1.0); }
	public static void setSoundVolume(double value) { KhoaConfig.putDouble(SECTION, "sound_volume", value); }
}
