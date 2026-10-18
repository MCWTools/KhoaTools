package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import khoa.tools.client.feature.bettertnt.BetterTntFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Better TNT: same as vanilla handleExplosion, but the sound / the particles can be limited. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerExplosionMixin {
	@Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
	private void khoa$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		if (!BetterTntFeature.active()) return;
		Minecraft mc = Minecraft.getInstance();
		PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, mc.packetProcessor());
		ci.cancel();
		ClientLevel level = mc.level;
		if (level == null) return;
		Vec3 center = packet.center();
		if (BetterTntFeature.explosionSoundAllowed()) {
			level.playLocalSound(center.x(), center.y(), center.z(), packet.explosionSound().value(), SoundSource.BLOCKS, 4.0F,
				(1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F) * 0.7F, false);
		}
		if (!BetterTntFeature.hideExplosionParticles()) {
			level.addParticle(packet.explosionParticle(), center.x(), center.y(), center.z(), 1.0, 0.0, 0.0);
			level.trackExplosionEffects(center, packet.radius(), packet.blockCount(), packet.blockParticles());
		}
		packet.playerKnockback().ifPresent(mc.player::addDeltaMovement);
	}
}
