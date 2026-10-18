package khoa.tools.client.feature.visiblebarrier;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import khoa.tools.client.feature.KhoaFeature;

/**
 * Shows hidden barrier blocks. Uses the same marker particle vanilla shows when a creative player holds a barrier,
 * but spawns it for every barrier around you (not random samples), in any game mode.
 * The marker particle lives 80 ticks, so it is re-spawned every 40 ticks.
 */
public final class VisibleBarrierFeature implements KhoaFeature {
	private static final int RADIUS = 32;
	private static final int PERIOD = 40;
	private static final int MAX_PARTICLES = 8000;
	private static final Predicate<BlockState> IS_BARRIER = s -> s.is(Blocks.BARRIER);

	private static boolean on;
	private static int timer;

	@Override
	public String id() {
		return "visiblebarrier";
	}

	@Override
	public String displayName() {
		return "Visible Barrier";
	}

	@Override
	public String description() {
		return "Shows hidden barrier blocks within " + RADIUS + " blocks of you, in any game mode. Click the card to turn it on/off. No settings.";
	}

	@Override
	public boolean persistEnabled() {
		return true;
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		timer = 0;
		KhoaFeature.say(mc, "Visible Barrier: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!on || mc.level == null || mc.player == null || mc.isPaused()) {
				return;
			}
			if (timer-- > 0) {
				return;
			}
			timer = PERIOD;
			spawn(mc.level, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
		});
	}

	private static void spawn(ClientLevel level, int px, int py, int pz) {
		int minY = level.getMinY();
		int spawned = 0;
		for (int cx = (px - RADIUS) >> 4; cx <= (px + RADIUS) >> 4; cx++) {
			for (int cz = (pz - RADIUS) >> 4; cz <= (pz + RADIUS) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					int baseY = minY + (i << 4);
					if (baseY + 15 < py - RADIUS || baseY > py + RADIUS || section.hasOnlyAir() || !section.maybeHas(IS_BARRIER)) {
						continue;
					}
					for (int ly = 0; ly < 16; ly++) {
						for (int lz = 0; lz < 16; lz++) {
							for (int lx = 0; lx < 16; lx++) {
								BlockState state = section.getBlockState(lx, ly, lz);
								if (!state.is(Blocks.BARRIER)) {
									continue;
								}
								int x = (cx << 4) + lx;
								int y = baseY + ly;
								int z = (cz << 4) + lz;
								if (Math.abs(x - px) > RADIUS || Math.abs(y - py) > RADIUS || Math.abs(z - pz) > RADIUS) {
									continue;
								}
								level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), x + 0.5, y + 0.5, z + 0.5, 0, 0, 0);
								if (++spawned >= MAX_PARTICLES) {
									return;
								}
							}
						}
					}
				}
			}
		}
	}
}
