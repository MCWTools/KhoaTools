package khoa.tools.client.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;

import khoa.tools.client.feature.customitemtab.CustomTabs;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	@Shadow
	protected abstract void selectTab(CreativeModeTab tab);

	/**
	 * Edit mode: hold the key and click an item. In one of our tabs it removes the item from that tab, in any other tab it adds
	 * the item to the tab being edited. The click itself is swallowed.
	 */
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void khoa$altClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (!CustomTabs.isEditing() || !CustomTabs.addKeyDown() || slot == null || !slot.hasItem()) {
			return;
		}
		CreativeModeTab shown = CreativeModeInventoryScreenAccessor.khoa$getSelectedTab();
		if (CustomTabs.isCustomTab(shown)) {
			CustomTabs.removeItem(shown, slot.getItem());
			selectTab(shown); // redraw the grid without the removed item
		} else {
			CustomTabs.addItem(slot.getItem());
		}
		ci.cancel();
	}

	/** Fill one of our tabs with its saved items right before it is shown. */
	@Inject(method = "selectTab", at = @At("HEAD"))
	private void khoa$prepareCustomTab(CreativeModeTab tab, CallbackInfo ci) {
		CustomTabs.prepare(tab);
	}

	/** Reminder above the creative inventory while edit mode is on. It follows the GUI position and scale. */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void khoa$editReminder(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!CustomTabs.isEditing()) {
			return;
		}
		Screen screen = (Screen) (Object) this;
		Font font = Minecraft.getInstance().font;
		CreativeModeTab shown = CreativeModeInventoryScreenAccessor.khoa$getSelectedTab();
		String key = CustomTabs.addKeyName();
		String text = CustomTabs.isCustomTab(shown)
			? "EDIT MODE: HOLD " + key + " AND CLICK ON AN ITEM TO REMOVE FROM TAB " + CustomTabs.customTabTitle(shown).toUpperCase(java.util.Locale.ROOT)
			: "EDIT MODE: HOLD " + key + " AND CLICK ON AN ITEM TO ADD TO TAB " + CustomTabs.customTabTitle(CustomTabs.editingTab()).toUpperCase(java.util.Locale.ROOT);
		List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(text), Math.max(120, screen.width - 8)));
		List<FormattedCharSequence> warn = font.split(Component.literal("You may need to rejoin to apply changes"), Math.max(120, screen.width - 8));
		int top = (screen.height - 136) / 2 - 28; // creative GUI is 136 high; its tab row is 28 above it
		int y = Math.max(2, top - 4 - (lines.size() + warn.size()) * 10);
		for (FormattedCharSequence line : lines) {
			g.text(font, line, (screen.width - font.width(line)) / 2, y, 0xFFFFFFFF);
			y += 10;
		}
		for (FormattedCharSequence line : warn) {
			g.text(font, line, (screen.width - font.width(line)) / 2, y, 0xFFFFC857);
			y += 10;
		}
	}
}
