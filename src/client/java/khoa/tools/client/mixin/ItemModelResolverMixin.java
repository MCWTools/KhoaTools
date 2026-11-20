package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import khoa.tools.client.feature.totemtweaks.TotemTweaksFeature;

/**
 * Totem Tweaks: a totem of undying in the hand is drawn as the chosen item.
 * First person goes through updateForTopItem (only the first person hand contexts are changed, so slots, the ground and
 * item frames keep the normal totem); third person goes through updateForLiving.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@Shadow
	public abstract void updateForLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, LivingEntity entity);

	@Shadow
	public abstract void updateForTopItem(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed);

	@Inject(method = "updateForLiving", at = @At("HEAD"), cancellable = true)
	private void khoa$heldTotem(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, LivingEntity entity, CallbackInfo ci) {
		if (TotemTweaksFeature.enabled() && stack.is(Items.TOTEM_OF_UNDYING)) {
			ItemStack replacement = TotemTweaksFeature.replacement();
			if (replacement != null) {
				this.updateForLiving(state, replacement, context, entity);
				ci.cancel();
			}
		}
	}

	@Inject(method = "updateForTopItem", at = @At("HEAD"), cancellable = true)
	private void khoa$heldTotemFirstPerson(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
		ItemOwner owner, int seed, CallbackInfo ci) {
		if (context.firstPerson() && TotemTweaksFeature.enabled() && stack.is(Items.TOTEM_OF_UNDYING)) {
			ItemStack replacement = TotemTweaksFeature.replacement();
			if (replacement != null) {
				this.updateForTopItem(state, replacement, context, level, owner, seed);
				ci.cancel();
			}
		}
	}
}
