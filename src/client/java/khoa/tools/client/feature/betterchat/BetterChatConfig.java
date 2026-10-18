package khoa.tools.client.feature.betterchat;

import com.google.gson.JsonObject;

import khoa.tools.client.config.KhoaConfig;

public final class BetterChatConfig {
	public static final String SECTION = "betterchat";
	public static final int VANILLA_LENGTH = 100;
	public static final int MAX_LENGTH = 100_000;

	private BetterChatConfig() {
	}

	/** Custom number of messages kept; 0 means vanilla default. */
	public static int length() {
		JsonObject section = KhoaConfig.section(SECTION);
		return section.has("length") ? Math.max(0, section.get("length").getAsInt()) : 0;
	}

	/** Limit in force for this session. Raising "Messages kept" applies at once; lowering it only takes effect after the next join. */
	private static int sessionLimit = -1;

	public static int limit() {
		if (sessionLimit < 0) {
			sessionLimit = effectiveLength();
		}
		return sessionLimit;
	}

	/** The setting changed: a higher value applies immediately, a lower one never deletes anything now. */
	public static void raiseLimit() {
		sessionLimit = Math.max(limit(), effectiveLength());
	}

	/** On join: apply the configured value (this is where a lowered value finally cuts old messages). */
	public static void resetLimit() {
		sessionLimit = effectiveLength();
	}

	/** How many messages the setting asks for (custom length or vanilla default). */
	public static int effectiveLength() {
		int length = length();
		return length > 0 ? length : VANILLA_LENGTH;
	}

	public static boolean cache() {
		JsonObject section = KhoaConfig.section(SECTION);
		return section.has("cache") && section.get("cache").getAsBoolean();
	}

	public static void setLength(int length) {
		KhoaConfig.section(SECTION).addProperty("length", length);
		KhoaConfig.save();
	}

	public static void setCache(boolean cache) {
		KhoaConfig.section(SECTION).addProperty("cache", cache);
		KhoaConfig.save();
	}
}
