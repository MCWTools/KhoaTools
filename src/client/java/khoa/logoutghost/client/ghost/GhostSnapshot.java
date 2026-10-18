package khoa.logoutghost.client.ghost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;

/** Everything needed to rebuild the look of a player after they are gone. */
public record GhostSnapshot(
		String name,
		PlayerSkin skin,
		double x, double y, double z,
		float yRot, float xRot, float bodyRot, float headRot,
		Pose pose,
		boolean sprinting,
		boolean swimming,
		boolean fallFlying,
		int modelMask,
		HumanoidArm mainArm,
		Optional<BlockPos> sleepingPos,
		float walkPos, float walkSpeed, float attackTime, float ageInTicks
) {
	public static GhostSnapshot capture(AbstractClientPlayer p) {
		int mask = 0;
		for (PlayerModelPart part : PlayerModelPart.values()) {
			if (p.isModelPartShown(part)) {
				mask |= part.getMask();
			}
		}
		Player player = p;
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return new GhostSnapshot(
				player.getGameProfile().name(),
				p.getSkin(),
				p.getX(), p.getY(), p.getZ(),
				p.getYRot(), p.getXRot(), p.yBodyRot, p.yHeadRot,
				p.getPose(),
				p.isSprinting(),
				p.isSwimming(),
				p.isFallFlying(),
				mask,
				p.getMainArm(),
				p.getSleepingPos(),
				p.walkAnimation.position(partial),
				p.walkAnimation.speed(partial),
				p.getAttackAnim(partial),
				p.tickCount + partial
		);
	}
}
