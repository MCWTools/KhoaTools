package khoa.tools.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import khoa.tools.client.feature.totemtweaks.TotemTweaksFeature;

/** Totem Tweaks: the pop animation shows another item and/or is drawn smaller. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	/** True while the animation that is playing came from a totem of undying. */
	@Unique
	private boolean khoa$totemPop;

	@ModifyVariable(method = "displayItemActivation", at = @At("HEAD"), argsOnly = true)
	private ItemStack khoa$totemItem(ItemStack stack) {
		khoa$totemPop = TotemTweaksFeature.enabled() && stack.is(Items.TOTEM_OF_UNDYING);
		if (khoa$totemPop) {
			ItemStack replacement = TotemTweaksFeature.replacement();
			if (replacement != null) {
				return replacement;
			}
		}
		return stack;
	}

	@Inject(method = "renderItemActivationAnimation", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER))
	private void khoa$totemSize(PoseStack poseStack, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		if (khoa$totemPop && TotemTweaksFeature.enabled()) {
			float scale = TotemTweaksFeature.scale();
			poseStack.scale(scale, scale, scale);
		}
	}
}
