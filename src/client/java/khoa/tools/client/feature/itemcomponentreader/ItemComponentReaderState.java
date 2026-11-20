package khoa.tools.client.feature.itemcomponentreader;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Last hovered stack in an inventory GUI, plus which player-inventory slot it came from (-1 = other container).
 * It is only valid while the container screen is open and for a few seconds after closing it, and it is used once,
 * otherwise an old hover (a barrel you passed the mouse over) would hide the item you are actually holding.
 */
public final class ItemComponentReaderState {
	private static final long VALID_MS = 10_000L;
	private static ItemStack last = ItemStack.EMPTY;
	private static int lastInventorySlot = -1;
	private static long closedAt; // 0 = a container screen is open (or nothing captured)

	private ItemComponentReaderState() { }

	public static void capture(ItemStack stack) {
		last = stack == null ? ItemStack.EMPTY : stack.copy();
		lastInventorySlot = -1;
		closedAt = 0;
	}

	public static void capture(Slot slot) {
		last = slot == null ? ItemStack.EMPTY : slot.getItem().copy();
		Minecraft mc = Minecraft.getInstance();
		lastInventorySlot = slot != null && mc.player != null && slot.container == mc.player.getInventory() ? slot.getContainerSlot() : -1;
		closedAt = 0;
	}

	/** Called when a container screen closes: the hover now starts to expire. */
	public static void screenClosed() {
		if (!last.isEmpty()) closedAt = System.currentTimeMillis();
	}

	private static boolean fresh() { return closedAt == 0 || System.currentTimeMillis() - closedAt < VALID_MS; }

	public static ItemStack lastStack() { return fresh() ? last.copy() : ItemStack.EMPTY; }

	/** Player inventory index (0-8 hotbar, 9-35 main, 36-39 armor feet..head, 40 offhand) or -1. */
	public static int lastInventorySlot() { return fresh() ? lastInventorySlot : -1; }

	public static void clear() {
		last = ItemStack.EMPTY;
		lastInventorySlot = -1;
		closedAt = 0;
	}
}
