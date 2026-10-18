package khoa.tools.client.mixin;

import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	/** True while a FIREWORK particle that should be skipped is being created: it is built normally but never added. */
	@Unique
	private boolean khoa$dropNextAdd;

	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void khoa$limitParticles(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
		CallbackInfoReturnable<Particle> cir) {
		if (!OptimizeFpsFeature.skipParticle(x, y, z)) return;
		// FireworkParticles.Starter does ((SparkParticle) createParticle(FIREWORK ...)).setTrail(..) with no null check,
		// so returning null for it crashes the game ("Ticking Particle", NPE). Create it but do not add it to the world.
		if (options.getType() == ParticleTypes.FIREWORK) {
			khoa$dropNextAdd = true;
			return;
		}
		cir.setReturnValue(null);
	}

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void khoa$dropFirework(Particle particle, CallbackInfo ci) {
		if (khoa$dropNextAdd) {
			khoa$dropNextAdd = false;
			ci.cancel();
		}
	}

	@Inject(method = "createParticle", at = @At("RETURN"))
	private void khoa$resetDrop(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
		CallbackInfoReturnable<Particle> cir) {
		khoa$dropNextAdd = false; // makeParticle may return null (add never called), never leave the flag set
	}
}
