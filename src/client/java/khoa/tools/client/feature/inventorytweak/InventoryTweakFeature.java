package khoa.tools.client.feature.inventorytweak;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * Inventory Tweak (creative mode only), both options are off by default:
 * <ul>
 * <li>CTRL + ALT + click on an item of your inventory deletes it.</li>
 * <li>CTRL + ALT + SPACE on a hovered shulker box moves its contents into the inventory (first slot that is empty or
 * has the same stackable item, from the first slot upwards; nothing is replaced). What does not fit stays inside.</li>
 * </ul>
 * The key handling is in {@code InventoryTweakScreenMixin}.
 */
public final class InventoryTweakFeature implements KhoaFeature {
	private static final String ID = "inventorytweak";
	private static boolean deleteOnClick;
	private static boolean unboxOnSpace;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Inventory Tweak";
	}

	@Override
	public String description() {
		return "Creative mode only. CTRL ALT CLICK on an inventory item deletes it. CTRL ALT SPACE on a hovered shulker box "
			+ "moves its items into your inventory (never replaces items; what does not fit stays in the shulker).";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.bool("delete", "CTRL ALT CLICK to delete item in creative", "Ctrl + Alt + left click on an item in your inventory deletes it (creative mode).",
				() -> deleteOnClick, value -> {
					deleteOnClick = value;
					KhoaConfig.put(ID, "delete", value);
					KhoaConfig.save();
				}),
			Setting.bool("unbox", "CTRL ALT SPACE to unbox shulker in creative", "Ctrl + Alt + Space while hovering a shulker box moves its items into the inventory (creative mode).",
				() -> unboxOnSpace, value -> {
					unboxOnSpace = value;
					KhoaConfig.put(ID, "unbox", value);
					KhoaConfig.save();
				}));
	}

	@Override
	public void onInit() {
		deleteOnClick = KhoaConfig.getBool(ID, "delete", false);
		unboxOnSpace = KhoaConfig.getBool(ID, "unbox", false);
	}

	/** Slot index in the player's InventoryMenu (what the creative slot packet expects) for an Inventory index. */
	private static int menuSlot(int inventoryIndex) {
		if (inventoryIndex < 9) {
			return 36 + inventoryIndex;
		}
		if (inventoryIndex < 36) {
			return inventoryIndex;
		}
		if (inventoryIndex < 40) {
			return 44 - inventoryIndex; // boots 36 -> 8 ... helmet 39 -> 5
		}
		return 45; // off hand
	}

	/**
	 * Index (0-40) of the hovered slot in the player's Inventory, or -1. Found by the stack instance itself: the creative
	 * screen shows inventory slots through wrapper slots whose container index is a menu index, not an Inventory index.
	 */
	private static int inventoryIndexOf(Minecraft mc, Slot slot) {
		if (mc.player == null || mc.gameMode == null || !mc.player.getAbilities().instabuild || slot == null || !slot.hasItem()) {
			return -1;
		}
		ItemStack stack = slot.getItem();
		Inventory inventory = mc.player.getInventory();
		for (int i = 0; i <= 40; i++) {
			if (inventory.getItem(i) == stack) {
				return i;
			}
		}
		return -1;
	}

	/** Like vanilla creative: change the slot on the client at once and tell the server. */
	private static void setSlot(Minecraft mc, int inventoryIndex, ItemStack stack) {
		mc.player.getInventory().setItem(inventoryIndex, stack);
		mc.gameMode.handleCreativeModeItemAdd(stack, menuSlot(inventoryIndex));
	}

	/** @return true if the click was used */
	public static boolean deleteItem(Minecraft mc, Slot slot) {
		if (!deleteOnClick) {
			return false;
		}
		int index = inventoryIndexOf(mc, slot);
		if (index < 0) {
			return false;
		}
		Component name = slot.getItem().getHoverName();
		setSlot(mc, index, ItemStack.EMPTY);
		mc.gui.setOverlayMessage(Component.literal("Deleted ").append(name), false);
		return true;
	}

	/** @return true if the key press was used */
	public static boolean unboxShulker(Minecraft mc, Slot slot) {
		int shulkerIndex = inventoryIndexOf(mc, slot);
		if (!unboxOnSpace || shulkerIndex < 0) {
			return false;
		}

		ItemStack shulker = slot.getItem();
		if (!(shulker.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) {
			return false;
		}
		ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
		if (contents == null) {
			return false;
		}
		List<ItemStack> items = contents.nonEmptyItemCopyStream().toList();
		if (items.isEmpty()) {
			return true;
		}

		Inventory inventory = mc.player.getInventory();
		ItemStack[] work = new ItemStack[36];
		for (int i = 0; i < work.length; i++) {
			work[i] = inventory.getItem(i).copy();
		}
		boolean[] changed = new boolean[36];

		List<ItemStack> leftover = new ArrayList<>();
		int moved = 0;
		for (ItemStack item : items) {
			ItemStack remaining = item.copy();
			while (!remaining.isEmpty()) {
				// nearest usable slot: first one from the start that is empty or can still take this item
				int target = -1;
				for (int i = 0; i < work.length; i++) {
					if (i == shulkerIndex) {
						continue;
					}
					ItemStack existing = work[i];
					if (existing.isEmpty() || (ItemStack.isSameItemSameComponents(existing, remaining)
						&& existing.getCount() < existing.getMaxStackSize())) {
						target = i;
						break;
					}
				}
				if (target < 0) {
					break;
				}

				ItemStack existing = work[target];
				if (existing.isEmpty()) {
					int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
					work[target] = remaining.split(amount);
					moved += amount;
				} else {
					int amount = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
					existing.grow(amount);
					remaining.shrink(amount);
					moved += amount;
				}
				changed[target] = true;
			}
			if (!remaining.isEmpty()) {
				leftover.add(remaining);
			}
		}

		for (int i = 0; i < work.length; i++) {
			if (changed[i]) {
				setSlot(mc, i, work[i]);
			}
		}

		ItemStack emptied = shulker.copy();
		if (leftover.isEmpty()) {
			emptied.remove(DataComponents.CONTAINER);
		} else {
			emptied.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(leftover));
		}
		setSlot(mc, shulkerIndex, emptied);

		mc.gui.setOverlayMessage(Component.literal(leftover.isEmpty()
			? "Unboxed shulker: " + moved + " items moved to your inventory"
			: "Inventory full: " + moved + " items moved, the rest stays in the shulker"), false);
		return true;
	}
}
