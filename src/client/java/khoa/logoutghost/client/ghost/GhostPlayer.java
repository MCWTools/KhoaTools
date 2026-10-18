package khoa.logoutghost.client.ghost;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * Client-only, frozen copy of a player who logged out. It never ticks, cannot be targeted,
 * and is rendered as a translucent "invisible" entity whose alpha is driven by {@link #alpha(float)}.
 */
public class GhostPlayer extends RemotePlayer {
	private final PlayerSkin ghostSkin;
	private final boolean ghostFallFlying;
	private final int fadeTicks;
	private final float baseAlpha;
	// frozen animation values captured at logout time
	public final float frozenWalkPos;
	public final float frozenWalkSpeed;
	public final float frozenAttackTime;
	public final float frozenAgeInTicks;
	/** Age in (unpaused) client ticks. */
	public int ghostAge;

	public GhostPlayer(ClientLevel level, GhostSnapshot s, int fadeTicks, float baseAlpha) {
		// random UUID so we never clash with a real entity; name is what gets displayed
		super(level, new GameProfile(UUID.randomUUID(), s.name()));
		this.ghostSkin = s.skin();
		this.ghostFallFlying = s.fallFlying();
		this.fadeTicks = fadeTicks;
		this.baseAlpha = baseAlpha;
		this.frozenWalkPos = s.walkPos();
		this.frozenWalkSpeed = s.walkSpeed();
		this.frozenAttackTime = s.attackTime();
		this.frozenAgeInTicks = s.ageInTicks();

		this.noPhysics = true;
		this.setNoGravity(true);

		this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) s.modelMask());
		this.setMainArm(s.mainArm());

		this.setPose(s.pose());
		this.setSprinting(s.sprinting());
		this.setSwimming(s.swimming());
		this.setSharedFlag(7, s.fallFlying());
		s.sleepingPos().ifPresent(this::setSleepingPos);

		this.snapTo(s.x(), s.y(), s.z(), s.yRot(), s.xRot());
		this.yBodyRot = s.bodyRot();
		this.yBodyRotO = s.bodyRot();
		this.yHeadRot = s.headRot();
		this.yHeadRotO = s.headRot();
	}

	/** Current opacity 0..1 (partialTick smooths between ticks). */
	public float alpha(float partialTick) {
		if (fadeTicks <= 0) {
			return baseAlpha;
		}
		float t = ((float) ghostAge + partialTick) / (float) fadeTicks;
		return baseAlpha * Mth.clamp(1.0F - t, 0.0F, 1.0F);
	}

	/** 0..1 fade progress left (1 = fresh, 0 = gone); ignores baseAlpha. */
	public float fadeFactor(float partialTick) {
		if (fadeTicks <= 0) {
			return 1.0F;
		}
		return Mth.clamp(1.0F - ((float) ghostAge + partialTick) / (float) fadeTicks, 0.0F, 1.0F);
	}

	public boolean isFinished() {
		return fadeTicks > 0 && ghostAge >= fadeTicks;
	}

	@Override
	public PlayerSkin getSkin() {
		return ghostSkin;
	}

	@Override
	public void tick() {
		// frozen: no movement, no animation, no physics
	}

	@Override
	public void aiStep() {
	}

	@Override
	public boolean isInvisible() {
		// makes the vanilla renderer use its translucent path
		return true;
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return false;
	}

	@Override
	public float getSwimAmount(float partialTick) {
		return this.getPose() == Pose.SWIMMING ? 1.0F : 0.0F;
	}

	@Override
	public int getFallFlyingTicks() {
		return ghostFallFlying ? 10 : 0;
	}
}
