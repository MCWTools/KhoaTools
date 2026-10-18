package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import khoa.tools.client.chat.ChatCoordinates;
import khoa.tools.client.feature.chattranslator.ChatTranslatorFeature;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void khoatools$copyClickedCoordinate(Style style, boolean insert, CallbackInfoReturnable<Boolean> cir) {
		if (insert && ChatTranslatorFeature.isToken(style.getInsertion())) {
			cir.setReturnValue(true); // hidden hover-translate marker: never paste it into the chat box
			return;
		}
		if (insert || !(style.getClickEvent() instanceof ClickEvent.CopyToClipboard copy)
			|| !ChatCoordinates.isCoordinate(copy.value())) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.keyboardHandler.setClipboard(copy.value());
		minecraft.gui.setOverlayMessage(Component.literal("Copied clicked coordinate to clipboard"), false);
		cir.setReturnValue(true);
	}
}
