package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import khoa.tools.client.feature.chatsearch.ChatSearchFeature;

/** Chat Search highlight is applied while a message is wrapped into display lines; the stored message itself is never changed. */
@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin {
	@ModifyArg(method = "splitLines", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
		index = 0)
	private FormattedText khoatools$searchHighlight(FormattedText text) {
		return text instanceof Component component ? ChatSearchFeature.highlight(component) : text;
	}
}
