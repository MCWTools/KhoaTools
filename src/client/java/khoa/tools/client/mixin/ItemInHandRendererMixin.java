package khoa.tools.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import khoa.tools.client.feature.handsize.HandSizeFeature;

/**
 * Change Hand Size: everything drawn for one hand (arm, held item, map) is wrapped in a push/pop that moves it by the
 * X / Y / Z offsets and scales it around the usual hand spot of the screen, so it stays in its corner.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	// Where vanilla puts the hand in view space (before the item transform): x = +-0.64, y = -0.6, z = -0.72.
	private static final float KHOA_PIVOT_X = 0.64F;
	private static final float KHOA_PIVOT_Y = -0.6F;
	private static final float KHOA_PIVOT_Z = -0.72F;

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void khoa$handStart(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
		float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, SubmitNodeCollector collector,
		int light, CallbackInfo ci) {
		poseStack.pushPose();
		float scale = HandSizeFeature.scale();
		HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float px = side == HumanoidArm.RIGHT ? KHOA_PIVOT_X : -KHOA_PIVOT_X;
		poseStack.translate(HandSizeFeature.offsetX(), HandSizeFeature.offsetY(), HandSizeFeature.offsetZ());
		poseStack.translate(px, KHOA_PIVOT_Y, KHOA_PIVOT_Z);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(-px, -KHOA_PIVOT_Y, -KHOA_PIVOT_Z);
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void khoa$handEnd(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
		float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, SubmitNodeCollector collector,
		int light, CallbackInfo ci) {
		poseStack.popPose();
	}
}
