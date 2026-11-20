package khoa.tools.client.feature.armormaterial;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Change Armor Material (client side only): the armor you wear is drawn with the model / texture of another material,
 * chosen per piece. The item itself is untouched, so enchantment glint and armor trim are still drawn like vanilla.
 * The work is done in HumanoidArmorLayerMixin, which swaps the equipment asset id just before the armor layers are drawn.
 */
public final class ArmorMaterialFeature implements KhoaFeature {
	private static final String ID = "armormaterial";
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private record Material(String name, ResourceKey<EquipmentAsset> asset) {
	}

	private static final Material DEFAULT = new Material("Default", null);
	private static final Material TURTLE = new Material("Turtle", EquipmentAssets.TURTLE_SCUTE);
	/** Materials that have a texture for all four pieces. Turtle only exists as a helmet and is added to that list only. */
	private static final List<Material> ALL = List.of(
		DEFAULT,
		new Material("Leather", EquipmentAssets.LEATHER),
		new Material("Chainmail", EquipmentAssets.CHAINMAIL),
		new Material("Copper", EquipmentAssets.COPPER),
		new Material("Iron", EquipmentAssets.IRON),
		new Material("Gold", EquipmentAssets.GOLD),
		new Material("Diamond", EquipmentAssets.DIAMOND),
		new Material("Netherite", EquipmentAssets.NETHERITE));

	private static boolean on;
	private static final Map<EquipmentSlot, Material> CHOSEN = new EnumMap<>(EquipmentSlot.class);

	private static List<Material> optionsFor(EquipmentSlot slot) {
		if (slot != EquipmentSlot.HEAD) {
			return ALL;
		}
		List<Material> list = new ArrayList<>(ALL);
		list.add(TURTLE);
		return list;
	}

	private static String key(EquipmentSlot slot) {
		return slot.getName(); // head / chest / legs / feet
	}

	private static Material load(EquipmentSlot slot) {
		String saved = KhoaConfig.getString(ID, key(slot), DEFAULT.name());
		for (Material m : optionsFor(slot)) {
			if (m.name().equalsIgnoreCase(saved)) {
				return m;
			}
		}
		return DEFAULT;
	}

	/**
	 * The asset id that should be drawn for one worn armor piece of {@code renderState}.
	 * Only your own player is changed, only while the feature is on, and elytra are never touched.
	 */
	public static ResourceKey<EquipmentAsset> replace(Object renderState, EquipmentSlot slot, ResourceKey<EquipmentAsset> original) {
		if (!on || original == null || original.equals(EquipmentAssets.ELYTRA)) {
			return original;
		}
		if (!(renderState instanceof AvatarRenderState avatar)) {
			return original;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || avatar.id != mc.player.getId()) {
			return original;
		}
		Material chosen = CHOSEN.get(slot);
		return chosen == null || chosen.asset() == null ? original : chosen.asset();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Change Armor Material";
	}

	@Override
	public String description() {
		return "Client side only. Click the card to turn it on/off, then pick a material for each armor piece you wear. "
			+ "Only the look changes: enchantment glint and armor trim stay.";
	}

	@Override
	public List<Setting> settings() {
		List<Setting> list = new ArrayList<>();
		String[] labels = { "Helmet", "Chestplate", "Leggings", "Boots" };
		for (int i = 0; i < SLOTS.length; i++) {
			EquipmentSlot slot = SLOTS[i];
			List<Material> options = optionsFor(slot);
			list.add(Setting.dropdown(key(slot), labels[i], "Material shown for your " + labels[i].toLowerCase() + ".",
				options.stream().map(Material::name).toList(),
				() -> Math.max(0, options.indexOf(CHOSEN.getOrDefault(slot, DEFAULT))),
				index -> {
					Material m = options.get(Math.floorMod(index, options.size()));
					CHOSEN.put(slot, m);
					KhoaConfig.put(ID, key(slot), m.name());
				}));
		}
		return list;
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
		KhoaFeature.say(mc, "Change Armor Material: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		for (EquipmentSlot slot : SLOTS) {
			CHOSEN.put(slot, load(slot));
		}
	}
}
