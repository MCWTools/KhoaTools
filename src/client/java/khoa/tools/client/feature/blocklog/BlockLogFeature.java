package khoa.tools.client.feature.blocklog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.util.ServerKey;

/**
 * Records the blocks players (you and others you can see) break and place into
 * .minecraft/khoatools/blocklogs/&lt;server&gt;_&lt;start time&gt;.txt, one line each:
 * [2026-10-19 14:05:33] KhoaMCW break stone 10 64 -5
 *
 * Rejoining while it is on starts a new file; turning it off stops writing. Times are your computer's local time.
 *
 * How it knows: every block change the client receives (or predicts for you) goes through Level.setBlock, see LevelBlockLogMixin.
 * The change is matched to the closest player within {@link #REACH} blocks who is swinging their arm. Changes nobody caused
 * (water flow, falling blocks, explosions, crops...) are dropped, so the log only holds player actions.
 */
public final class BlockLogFeature implements KhoaFeature {
	private static final double REACH = 7.0;
	private static final int SETTLE_TICKS = 2; // the arm swing can arrive a tick after the block update
	private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	private record Pending(BlockPos pos, BlockState oldState, BlockState newState, int tick, LocalDateTime time) {
	}

	private static volatile boolean on;
	private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
	private static BufferedWriter writer;
	private static int tickCounter;

	@Override
	public String id() {
		return "blocklog";
	}

	@Override
	public String displayName() {
		return "Block Log";
	}

	@Override
	public String description() {
		return "While on, saves every block you and nearby players break or place to .minecraft/khoatools/blocklogs/<server>_<start time>.txt "
			+ "(line: [time] player break/place block x y z, your computer's time). Rejoining a server starts a new file, turning it off stops writing. "
			+ "Only player actions are logged: a change counts when a player within " + (int) REACH + " blocks is swinging their arm.";
	}

	@Override
	public boolean isActive() {
		return on;
	}

	@Override
	public void activate(Minecraft mc) {
		on = !on;
		if (on) {
			if (mc.level != null && mc.getConnection() != null) {
				openFile(mc);
			}
		} else {
			closeFile();
			PENDING.clear();
		}
		KhoaFeature.say(mc, "Block Log: " + (on ? "on" : "off"));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(BlockLogFeature::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			PENDING.clear();
			if (on) openFile(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			closeFile();
			PENDING.clear();
		});
	}

	// ---- called from LevelBlockLogMixin (client thread) ----

	public static boolean recording() {
		return on;
	}

	public static void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
		if (writer == null || oldState.getBlock() == newState.getBlock()) {
			return; // not recording, or only a block-state change (door opened, redstone...)
		}
		PENDING.add(new Pending(pos.immutable(), oldState, newState, tickCounter, LocalDateTime.now()));
	}

	// ---- matching changes to players ----

	private static void tick(Minecraft mc) {
		tickCounter++;
		if (writer == null || PENDING.isEmpty() || mc.level == null) {
			return;
		}
		boolean wrote = false;
		while (!PENDING.isEmpty() && tickCounter - PENDING.peekFirst().tick() >= SETTLE_TICKS) {
			Pending p = PENDING.pollFirst();
			String action = action(p.oldState(), p.newState());
			if (action == null) {
				continue;
			}
			AbstractClientPlayer who = actor(mc, p.pos());
			if (who == null) {
				continue;
			}
			BlockState shown = action.equals("break") ? p.oldState() : p.newState();
			write(LINE_TIME.format(p.time()) , who.getName().getString(), action, blockId(shown), p.pos());
			wrote = true;
		}
		if (wrote) {
			try {
				writer.flush();
			} catch (IOException e) {
				closeFile();
			}
		}
	}

	/** "break" / "place" for a real block going away / appearing, null for anything else (block swapped for another, fluids). */
	private static String action(BlockState from, BlockState to) {
		boolean fromEmpty = from.isAir() || from.getBlock() instanceof LiquidBlock;
		boolean toEmpty = to.isAir() || to.getBlock() instanceof LiquidBlock;
		if (!fromEmpty && toEmpty) {
			return "break";
		}
		if (fromEmpty && !toEmpty) {
			return "place";
		}
		return null;
	}

	private static AbstractClientPlayer actor(Minecraft mc, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		AbstractClientPlayer best = null;
		double bestSq = REACH * REACH;
		for (AbstractClientPlayer player : mc.level.players()) {
			if (!player.swinging && player.swingTime <= 0 && player.attackAnim <= 0.0f) {
				continue;
			}
			double d = player.getEyePosition().distanceToSqr(center);
			if (d <= bestSq) {
				bestSq = d;
				best = player;
			}
		}
		return best;
	}

	private static String blockId(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
	}

	// ---- file ----

	private static void write(String time, String player, String action, String block, BlockPos pos) {
		try {
			writer.write("[" + time + "] " + player + " " + action + " " + block + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
			writer.newLine();
		} catch (IOException e) {
			closeFile();
		}
	}

	private static void openFile(Minecraft mc) {
		closeFile();
		try {
			Path dir = mc.gameDirectory.toPath().resolve("khoatools").resolve("blocklogs");
			Files.createDirectories(dir);
			LocalDateTime start = LocalDateTime.now();
			String base = ServerKey.id(mc) + "_" + FILE_TIME.format(start);
			Path file = dir.resolve(base + ".txt");
			for (int n = 2; Files.exists(file); n++) {
				file = dir.resolve(base + "_" + n + ".txt");
			}
			writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			writer.write("# KhoaTools block log - " + ServerKey.display(mc) + " - started " + LINE_TIME.format(start));
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			writer = null;
			KhoaFeature.say(mc, "Block Log: could not create the log file (" + e.getMessage() + ")");
		}
	}

	private static void closeFile() {
		if (writer == null) {
			return;
		}
		try {
			writer.close();
		} catch (IOException ignored) {
		}
		writer = null;
	}
}
