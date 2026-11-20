package khoa.tools.client.feature.totemtweaks;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Totem Tweaks (client side only), for the totem of undying, in the pop animation and in the hand:
 * <ul>
 * <li>Totem item: any item id; the animation then shows that item's model/texture. Empty or unknown id = normal totem.</li>
 * <li>Totem size: 10% - 100% of the normal size (pop animation only).</li>
 * </ul>
 * The work is done in ScreenEffectRendererMixin (pop) and ItemModelResolverMixin (held totem).
 */
public final class TotemTweaksFeature implements KhoaFeature {
	private static final String ID = "totemtweaks";
	private static boolean on;
	private static String itemText = "";
	private static int percent = 100;

	// parsed item of itemText, re-resolved only when the text changes
	private static String resolvedFor = null;
	private static Item resolved = null;

	public static boolean enabled() {
		return on;
	}

	/** 1.0 = vanilla. */
	public static float scale() {
		return percent / 100.0F;
	}

	/** A new stack of the chosen item, or null when the normal totem should be shown. */
	public static ItemStack replacement() {
		String text = itemText;
		if (!text.equals(resolvedFor)) {
			resolved = resolve(text);
			resolvedFor = text;
		}
		return resolved == null ? null : new ItemStack(resolved);
	}

	private static Item resolve(String text) {
		String id = text.trim().toLowerCase(Locale.ROOT);
		if (id.isEmpty()) {
			return null;
		}
		if (id.indexOf(':') < 0) {
			id = "minecraft:" + id;
		}
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Totem Tweaks";
	}

	@Override
	public String description() {
		return "Client side only. Click the card to turn it on/off. Shows another item instead of the totem of undying, "
			+ "in the pop animation and in the hand (type its id, e.g. diamond_sword). Pop size can be made smaller.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.string("item", "Totem item", "Item id, e.g. diamond_sword or minecraft:apple. Empty / unknown = normal totem.",
				() -> itemText, value -> {
					itemText = value.trim();
					KhoaConfig.put(ID, "item", itemText);
				}),
			Setting.slider("size", "Totem size %", "Normal = 100.", 10, 100, () -> percent, value -> {
				percent = Math.max(10, Math.min(100, value));
				KhoaConfig.put(ID, "size", percent);
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
		KhoaFeature.say(mc, "Totem Tweaks: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		itemText = KhoaConfig.getString(ID, "item", "").trim();
		percent = Math.max(10, Math.min(100, KhoaConfig.getInt(ID, "size", 100)));
	}
}
