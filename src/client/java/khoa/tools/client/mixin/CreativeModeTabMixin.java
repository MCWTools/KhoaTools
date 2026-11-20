package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.CreativeModeTab;

import khoa.tools.client.feature.customitemtab.CustomTabs;

/** Custom item tabs are shown while they exist, even when they are still empty (vanilla hides empty category tabs). */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
	@Inject(method = "shouldDisplay", at = @At("HEAD"), cancellable = true)
	private void khoa$customTabVisible(CallbackInfoReturnable<Boolean> cir) {
		Boolean visible = CustomTabs.visibilityOf((CreativeModeTab) (Object) this);
		if (visible != null) {
			cir.setReturnValue(visible);
		}
	}
}
