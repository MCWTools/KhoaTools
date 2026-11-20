package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import khoa.tools.client.feature.inventorytweak.InventoryTweakFeature;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class InventoryTweakScreenMixin {

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void khoatools$ctrlAltClickDelete(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() == 0
			&& event.hasControlDown() && event.hasAltDown()
			&& InventoryTweakFeature.deleteItem(Minecraft.getInstance(), ((HoveredSlotAccessor) (Object) this).khoatools$hoveredSlot())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void khoatools$ctrlAltSpaceUnbox(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (event.key() == InputConstants.KEY_SPACE
			&& event.hasControlDown() && event.hasAltDown()
			&& InventoryTweakFeature.unboxShulker(Minecraft.getInstance(), ((HoveredSlotAccessor) (Object) this).khoatools$hoveredSlot())) {
			cir.setReturnValue(true);
		}
	}
}
