package khoa.tools.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import khoa.tools.client.feature.itemcomponentreader.ItemComponentReaderState;

@Mixin(AbstractContainerScreen.class)
public abstract class ItemComponentReaderContainerMixin {
	@Shadow protected Slot hoveredSlot;

	@Inject(method = "extractSlots", at = @At("TAIL"))
	private void khoatools$captureHovered(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (hoveredSlot != null && hoveredSlot.hasItem()) ItemComponentReaderState.capture(hoveredSlot);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void khoatools$hoverExpires(CallbackInfo ci) {
		ItemComponentReaderState.screenClosed();
	}
}
