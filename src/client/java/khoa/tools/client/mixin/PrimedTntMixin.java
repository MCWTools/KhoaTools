package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import khoa.tools.client.feature.bettertnt.BetterTntFeature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;

@Mixin(PrimedTnt.class)
public class PrimedTntMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void khoa$fuseSmoke(Level level, ParticleOptions options, double x, double y, double z, double xa, double ya, double za) {
		if (BetterTntFeature.hideFuseSmoke()) return;
		level.addParticle(options, x, y, z, xa, ya, za);
	}
}
