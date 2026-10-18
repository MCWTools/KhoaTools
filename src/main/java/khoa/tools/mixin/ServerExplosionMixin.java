package khoa.tools.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import khoa.tools.command.OptimizeTntFeature;

/** /khoatools optimizetnt: primed TNT explosions cast only a fraction of the 1352 vanilla rays. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;
	@Shadow
	@Final
	private Entity source;
	@Shadow
	@Final
	private Vec3 center;
	@Shadow
	@Final
	private float radius;
	@Shadow
	@Final
	private ExplosionDamageCalculator damageCalculator;

	@Unique
	private long khoa$raysStart;
	@Unique
	private long khoa$entitiesStart;
	@Unique
	private long khoa$explodeStart;

	// ---- timing of primed TNT explosions: /khoatools optimizetnt stats ----

	@Inject(method = "explode", at = @At("HEAD"))
	private void khoa$explodeStart(CallbackInfoReturnable<Integer> cir) {
		this.khoa$explodeStart = System.nanoTime();
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void khoa$explodeEnd(CallbackInfoReturnable<Integer> cir) {
		if (this.source instanceof PrimedTnt) {
			OptimizeTntFeature.addExplosion(System.nanoTime() - this.khoa$explodeStart);
		}
	}

	@Inject(method = "hurtEntities", at = @At("HEAD"))
	private void khoa$entitiesStart(CallbackInfo ci) {
		this.khoa$entitiesStart = System.nanoTime();
	}

	@Inject(method = "hurtEntities", at = @At("RETURN"))
	private void khoa$entitiesEnd(CallbackInfo ci) {
		if (this.source instanceof PrimedTnt) {
			OptimizeTntFeature.addEntities(System.nanoTime() - this.khoa$entitiesStart);
		}
	}

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
	private void khoa$raysEnd(CallbackInfoReturnable<List<BlockPos>> cir) {
		if (this.source instanceof PrimedTnt) {
			OptimizeTntFeature.addRays(System.nanoTime() - this.khoa$raysStart);
		}
	}

	/** Optional: a primed TNT pushed by an explosion gets one exposure ray instead of 27. */
	@Inject(method = "getSeenPercent", at = @At("HEAD"), cancellable = true)
	private static void khoa$cheapExposure(Vec3 center, Entity entity, CallbackInfoReturnable<Float> cir) {
		if (entity instanceof PrimedTnt && OptimizeTntFeature.tntExposure()) {
			Vec3 from = new Vec3(entity.getX(), entity.getY(0.5), entity.getZ());
			boolean seen = entity.level().clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
			cir.setReturnValue(seen ? 1.0F : 0.0F);
		}
	}

	@Inject(method = "calculateExplodedPositions", at = @At("HEAD"), cancellable = true)
	private void khoa$fewerRays(CallbackInfoReturnable<List<BlockPos>> cir) {
		this.khoa$raysStart = System.nanoTime();
		if (!(this.source instanceof PrimedTnt) || !OptimizeTntFeature.enabled()) {
			return;
		}
		double multiplier = OptimizeTntFeature.multiplier();
		if (multiplier >= 0.9999) {
			return;
		}
		OptimizeTntFeature.addOptimized();
		ServerExplosion self = (ServerExplosion) (Object) this;
		HashSet<BlockPos> toBlow = new HashSet<>();
		double acc = 1.0 - multiplier; // first ray always runs, then the rest are spread evenly over the cube faces
		for (int xx = 0; xx < 16; ++xx) {
			for (int yy = 0; yy < 16; ++yy) {
				ray:
				for (int zz = 0; zz < 16; ++zz) {
					if (xx != 0 && xx != 15 && yy != 0 && yy != 15 && zz != 0 && zz != 15) {
						continue;
					}
					acc += multiplier;
					if (acc < 1.0) {
						continue;
					}
					acc -= 1.0;
					double xd = (float) xx / 15.0F * 2.0F - 1.0F;
					double yd = (float) yy / 15.0F * 2.0F - 1.0F;
					double zd = (float) zz / 15.0F * 2.0F - 1.0F;
					double d = Math.sqrt(xd * xd + yd * yd + zd * zd);
					xd /= d;
					yd /= d;
					zd /= d;
					double xp = this.center.x;
					double yp = this.center.y;
					double zp = this.center.z;
					for (float power = this.radius * (0.7F + this.level.getRandom().nextFloat() * 0.6F); power > 0.0F; power -= 0.22500001F) {
						BlockPos pos = BlockPos.containing(xp, yp, zp);
						BlockState block = this.level.getBlockState(pos);
						FluidState fluid = this.level.getFluidState(pos);
						if (!this.level.isInWorldBounds(pos)) {
							continue ray;
						}
						Optional<Float> resistance = this.damageCalculator.getBlockExplosionResistance(self, this.level, pos, block, fluid);
						if (resistance.isPresent()) {
							power -= (resistance.get() + 0.3F) * 0.3F;
						}
						if (power > 0.0F && this.damageCalculator.shouldBlockExplode(self, this.level, pos, block, power)) {
							toBlow.add(pos);
						}
						xp += xd * 0.3F;
						yp += yd * 0.3F;
						zp += zd * 0.3F;
					}
				}
			}
		}
		OptimizeTntFeature.addRays(System.nanoTime() - this.khoa$raysStart);
		cir.setReturnValue(new ObjectArrayList<>(toBlow));
	}
}
