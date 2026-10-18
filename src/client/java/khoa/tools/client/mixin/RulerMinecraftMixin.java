package khoa.tools.client.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import khoa.tools.client.feature.ruler.RulerFeature;
@Mixin(Minecraft.class)
public abstract class RulerMinecraftMixin {
 @Inject(method="startAttack", at=@At("HEAD"), cancellable=true)
 private void khoatools$consumeRulerAttack(CallbackInfoReturnable<Boolean> cir){if(RulerFeature.consumesAttack((Minecraft)(Object)this))cir.setReturnValue(false);}
 @Inject(method="continueAttack", at=@At("HEAD"), cancellable=true)
 private void khoatools$consumeContinuedAttack(boolean leftClick, CallbackInfo ci){if(RulerFeature.consumesAttack((Minecraft)(Object)this))ci.cancel();}
}
