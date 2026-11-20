package khoa.tools.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

import khoa.tools.client.feature.armormaterial.ArmorMaterialFeature;

/**
 * Change Armor Material: renderLayers draws model texture, enchantment glint and trim from the stack and the asset id.
 * Only the asset id is swapped, so glint and trim are untouched.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Redirect(method = "renderArmorPiece", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
	private <T> void khoa$armorMaterial(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType layerType,
		ResourceKey<EquipmentAsset> asset, Model<? super T> model, T renderState, ItemStack stack, PoseStack poseStack,
		SubmitNodeCollector collector, int light, int outline) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			asset = ArmorMaterialFeature.replace(renderState, equippable.slot(), asset);
		}
		renderer.renderLayers(layerType, asset, model, renderState, stack, poseStack, collector, light, outline);
	}
}
