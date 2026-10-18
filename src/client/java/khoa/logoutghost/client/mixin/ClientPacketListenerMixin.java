package khoa.logoutghost.client.mixin;

import khoa.logoutghost.client.ghost.GhostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	// Packet handlers are first invoked on the network thread and re-run on the main thread;
	// only act on the main-thread run.

	@Inject(method = "handleRemoveEntities", at = @At("HEAD"))
	private void logoutghost$beforeRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) {
			GhostManager.onEntitiesRemoved(packet.getEntityIds());
		}
	}

	@Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
	private void logoutghost$beforePlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) {
			GhostManager.onPlayersLogout(packet.profileIds());
		}
	}
}
