package khoa.tools.client.feature.khoacopy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * KhoaCopy: click the card = copy every block in a sphere around you into the clipboard (as text starting with
 * "khoacopy:"). In another Minecraft (with this mod) press Paste in the config: it reads the clipboard, recognises
 * the prefix and rebuilds the blocks around the spot you stand on.
 *
 * Only the block id + its block state (facing, powered, ...) is stored. Contents of chests, signs, etc. are not.
 *
 * Clipboard text = "khoacopy:" + Base64(Deflate(text)). The text is: line 1 = format version, line 2 = palette
 * (block states without "minecraft:", separated by "|"), then one line per run of identical blocks along X:
 * "paletteIndex x y z length" (base 36, x/y/z relative to the block you stood on).
 *
 * Paste: singleplayer = places the blocks directly in your world (no op needed, no block updates, so pistons and
 * observers are not triggered while pasting). Multiplayer = sends /setblock and /fill (needs op).
 */
public final class KhoaCopyFeature implements KhoaFeature {
	private static final String ID = "khoacopy";
	private static final String PREFIX = "khoacopy:";
	private static final String VERSION = "1";
	private static final int MAX_RUN = 256;
	private static final int MAX_INFLATED = 64 * 1024 * 1024;
	/** Blocks placed per tick when pasting straight into a singleplayer world. */
	private static final int DIRECT_BLOCKS_PER_TICK = 4000;
	/** setBlock flags: 2 = send to clients, 16 = no shape updates. No neighbour updates. */
	private static final int DIRECT_FLAGS = 2 | 16;

	private record Run(int id, int x, int y, int z, int len) {
	}

	private record Parsed(List<String> palette, List<Run> runs) {
	}

	/** Ctrl+V was down on the previous tick (edge detection). */
	private boolean wasCtrlV;

	// paste state
	private boolean pasting;
	private boolean direct;
	private List<Run> runs = List.of();
	private List<String> palette = List.of();
	private BlockState[] states = new BlockState[0];
	private int index;
	private int blocksDone;
	private int blocksSkipped;
	private BlockPos origin = BlockPos.ZERO;
	private ClientLevel pasteLevel;
	private IntegratedServer server;
	private ResourceKey<Level> dimension;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "KhoaCopy";
	}

	@Override
	public String description() {
		return "Click the card: copies the blocks in a sphere around you to the clipboard (block id + state only). In another Minecraft with this mod, open this config and press Paste: it builds them around the block you stand on. Singleplayer pastes directly, multiplayer needs op (/setblock, /fill).";
	}

	@Override
	public boolean isActive() {
		return pasting;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.slider("radius", "Copy radius", "Radius of the sphere around you, in blocks.", 1, 32,
				KhoaCopyFeature::radius, v -> KhoaConfig.put(ID, "radius", v)),
			Setting.slider("speed", "Paste speed", "Multiplayer only: commands sent per tick while pasting.", 1, 20,
				KhoaCopyFeature::speed, v -> KhoaConfig.put(ID, "speed", v)),
			Setting.bool("ctrl_v", "Ctrl+V pastes", "While playing (no screen open), Ctrl+V pastes the clipboard if it is a khoacopy. Any other clipboard text is ignored.",
				() -> KhoaConfig.getBool(ID, "ctrl_v", true), v -> KhoaConfig.put(ID, "ctrl_v", v)),
			Setting.action("paste", "Paste", "Paste the clipboard (must start with khoacopy:) around the block you stand on.", "Paste", this::paste),
			Setting.action("cancel", "Cancel paste", "Stop a paste that is still running.", "Cancel", this::cancel));
	}

	private static int radius() {
		return Math.max(1, Math.min(32, KhoaConfig.getInt(ID, "radius", 8)));
	}

	private static int speed() {
		return Math.max(1, Math.min(20, KhoaConfig.getInt(ID, "speed", 5)));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(ID)
			.executes(ctx -> {
				copy(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("paste").executes(ctx -> {
				paste();
				return 1;
			}))
			.then(ClientCommands.literal("cancel").executes(ctx -> {
				cancel();
				return 1;
			})));
	}

	@Override
	public void activate(Minecraft mc) {
		copy(mc);
	}

	// ---------------------------------------------------------------- copy

	private void copy(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}
		int r = radius();
		int r2 = r * r;
		BlockPos o = mc.player.blockPosition();
		int minY = level.getMinY();
		int maxY = minY + level.getHeight() - 1;

		Map<BlockState, Integer> ids = new HashMap<>();
		List<String> pal = new ArrayList<>();
		List<Boolean> solid = new ArrayList<>();
		// Solid blocks first, then everything else (torches, levers, rails...), so supports exist before what hangs on them.
		List<Run> solidRuns = new ArrayList<>();
		List<Run> otherRuns = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int total = 0;

		for (int y = -r; y <= r; y++) {
			for (int z = -r; z <= r; z++) {
				int rem = r2 - y * y - z * z;
				if (rem < 0) {
					continue;
				}
				int w = (int) Math.floor(Math.sqrt(rem));
				int runId = -1;
				int runX = 0;
				int runLen = 0;
				for (int x = -w; x <= w; x++) {
					int bx = o.getX() + x;
					int by = o.getY() + y;
					int bz = o.getZ() + z;
					int id = -1;
					if (by >= minY && by <= maxY && level.hasChunk(bx >> 4, bz >> 4)) {
						p.set(bx, by, bz);
						BlockState st = level.getBlockState(p);
						if (!st.isAir()) {
							Integer got = ids.get(st);
							if (got == null) {
								got = pal.size();
								ids.put(st, got);
								pal.add(strip(BlockStateParser.serialize(st)));
								solid.add(st.isSolidRender());
							}
							id = got;
						}
					}
					if (id >= 0 && id == runId && runLen < MAX_RUN) {
						runLen++;
					} else {
						total += flush(solid, solidRuns, otherRuns, runId, runX, y, z, runLen);
						if (id >= 0) {
							runId = id;
							runX = x;
							runLen = 1;
						} else {
							runId = -1;
							runLen = 0;
						}
					}
				}
				total += flush(solid, solidRuns, otherRuns, runId, runX, y, z, runLen);
			}
		}

		if (total == 0) {
			KhoaFeature.say(mc, "KhoaCopy: nothing to copy (only air in radius " + r + ").");
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(VERSION).append('\n');
		for (int i = 0; i < pal.size(); i++) {
			if (i > 0) {
				sb.append('|');
			}
			sb.append(pal.get(i));
		}
		sb.append('\n');
		int runCount = 0;
		for (List<Run> group : List.of(solidRuns, otherRuns)) {
			for (Run run : group) {
				sb.append(b36(run.id())).append(' ').append(b36(run.x())).append(' ').append(b36(run.y())).append(' ')
					.append(b36(run.z())).append(' ').append(b36(run.len())).append('\n');
				runCount++;
			}
		}

		String text = PREFIX + Base64.getEncoder().withoutPadding().encodeToString(deflate(sb.toString().getBytes(StandardCharsets.UTF_8)));
		mc.keyboardHandler.setClipboard(text);
		KhoaFeature.say(mc, "KhoaCopy: copied " + total + " blocks (" + pal.size() + " block types, " + runCount + " runs, " + text.length() + " chars), radius " + r + ".");
	}

	private static int flush(List<Boolean> solid, List<Run> solidRuns, List<Run> otherRuns, int id, int x, int y, int z, int len) {
		if (id < 0 || len <= 0) {
			return 0;
		}
		(solid.get(id) ? solidRuns : otherRuns).add(new Run(id, x, y, z, len));
		return len;
	}

	private static String strip(String state) {
		return state.startsWith("minecraft:") ? state.substring("minecraft:".length()) : state;
	}

	private static String b36(int v) {
		return Integer.toString(v, 36);
	}

	private static byte[] deflate(byte[] raw) {
		Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
		d.setInput(raw);
		d.finish();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		while (!d.finished()) {
			int n = d.deflate(buf);
			bos.write(buf, 0, n);
		}
		d.end();
		return bos.toByteArray();
	}

	private static String inflate(byte[] data) throws DataFormatException {
		Inflater inf = new Inflater();
		inf.setInput(data);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		try {
			while (!inf.finished()) {
				int n = inf.inflate(buf);
				if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
					throw new DataFormatException("truncated");
				}
				bos.write(buf, 0, n);
				if (bos.size() > MAX_INFLATED) {
					throw new DataFormatException("too big");
				}
			}
		} finally {
			inf.end();
		}
		return bos.toString(StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- paste

	private void paste() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (pasting) {
			KhoaFeature.say(mc, "KhoaCopy: a paste is already running (use Cancel to stop it).");
			return;
		}
		String clip = mc.keyboardHandler.getClipboard();
		if (clip == null || !clip.startsWith(PREFIX)) {
			KhoaFeature.say(mc, "KhoaCopy: the clipboard is not a khoacopy (it must start with \"" + PREFIX + "\").");
			return;
		}
		Parsed parsed;
		try {
			parsed = decode(clip.substring(PREFIX.length()).trim());
		} catch (RuntimeException | DataFormatException e) {
			KhoaFeature.say(mc, "KhoaCopy: the clipboard starts with khoacopy: but the data is broken or cut off.");
			return;
		}
		if (parsed.runs().isEmpty()) {
			KhoaFeature.say(mc, "KhoaCopy: the clipboard has no blocks.");
			return;
		}

		palette = parsed.palette();
		runs = parsed.runs();
		index = 0;
		blocksDone = 0;
		blocksSkipped = 0;
		origin = mc.player.blockPosition();
		pasteLevel = mc.level;
		dimension = mc.level.dimension();
		server = mc.hasSingleplayerServer() ? mc.getSingleplayerServer() : null;
		direct = server != null;

		if (direct) {
			HolderLookup<Block> lookup = mc.level.registryAccess().lookupOrThrow(Registries.BLOCK);
			states = new BlockState[palette.size()];
			int unknown = 0;
			for (int i = 0; i < states.length; i++) {
				try {
					states[i] = BlockStateParser.parseForBlock(lookup, palette.get(i), false).blockState();
				} catch (CommandSyntaxException | RuntimeException e) {
					states[i] = null;
					unknown++;
				}
			}
			if (unknown > 0) {
				KhoaFeature.say(mc, "KhoaCopy: " + unknown + " block type(s) are unknown here (missing mod / other version) and will be skipped.");
			}
		} else {
			states = new BlockState[0];
			KhoaFeature.say(mc, "KhoaCopy: multiplayer paste uses /setblock and /fill, you need op.");
		}

		pasting = true;
		KhoaFeature.say(mc, "KhoaCopy: pasting " + runs.size() + " runs around " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + (direct ? " (direct)" : " (commands)") + "...");
	}

	private void checkCtrlV(Minecraft mc) {
		if (mc.getWindow() == null) {
			return;
		}
		boolean down = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_V)
			&& (InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RCONTROL));
		boolean fire = down && !wasCtrlV;
		wasCtrlV = down;
		if (!fire || pasting || mc.screen != null || mc.player == null || mc.level == null || !mc.isWindowActive()
			|| !KhoaConfig.getBool(ID, "ctrl_v", true)) {
			return;
		}
		String clip = mc.keyboardHandler.getClipboard();
		if (clip != null && clip.startsWith(PREFIX)) {
			paste();
		}
	}

	private void cancel() {
		if (!pasting) {
			return;
		}
		pasting = false;
		Minecraft mc = Minecraft.getInstance();
		KhoaFeature.say(mc, "KhoaCopy: paste cancelled after " + blocksDone + " blocks.");
		clearPaste();
	}

	private void clearPaste() {
		runs = List.of();
		palette = List.of();
		states = new BlockState[0];
		server = null;
		pasteLevel = null;
	}

	private static Parsed decode(String base64) throws DataFormatException {
		String text = inflate(Base64.getDecoder().decode(base64));
		String[] lines = text.split("\n", -1);
		if (lines.length < 2 || !VERSION.equals(lines[0])) {
			throw new IllegalArgumentException("unknown format");
		}
		List<String> pal = new ArrayList<>();
		if (!lines[1].isEmpty()) {
			for (String s : lines[1].split("\\|")) {
				pal.add(s);
			}
		}
		List<Run> out = new ArrayList<>();
		for (int i = 2; i < lines.length; i++) {
			if (lines[i].isEmpty()) {
				continue;
			}
			String[] f = lines[i].split(" ");
			if (f.length != 5) {
				throw new IllegalArgumentException("bad line");
			}
			int id = Integer.parseInt(f[0], 36);
			if (id < 0 || id >= pal.size()) {
				throw new IllegalArgumentException("bad palette index");
			}
			int len = Integer.parseInt(f[4], 36);
			if (len < 1 || len > MAX_RUN) {
				throw new IllegalArgumentException("bad length");
			}
			out.add(new Run(id, Integer.parseInt(f[1], 36), Integer.parseInt(f[2], 36), Integer.parseInt(f[3], 36), len));
		}
		return new Parsed(pal, out);
	}

	private void tick(Minecraft mc) {
		checkCtrlV(mc);
		if (!pasting) {
			return;
		}
		if (mc.level != pasteLevel || mc.player == null) {
			pasting = false;
			clearPaste();
			KhoaFeature.say(mc, "KhoaCopy: paste stopped (world changed) after " + blocksDone + " blocks.");
			return;
		}

		if (direct) {
			List<BlockPos> posList = new ArrayList<>();
			List<BlockState> stateList = new ArrayList<>();
			int budget = DIRECT_BLOCKS_PER_TICK;
			while (index < runs.size() && budget > 0) {
				Run run = runs.get(index++);
				BlockState st = states[run.id()];
				if (st == null) {
					blocksSkipped += run.len();
					continue;
				}
				for (int k = 0; k < run.len(); k++) {
					posList.add(new BlockPos(origin.getX() + run.x() + k, origin.getY() + run.y(), origin.getZ() + run.z()));
					stateList.add(st);
				}
				budget -= run.len();
			}
			if (!posList.isEmpty()) {
				blocksDone += posList.size();
				IntegratedServer srv = server;
				ResourceKey<Level> dim = dimension;
				srv.execute(() -> {
					ServerLevel sl = srv.getLevel(dim);
					if (sl == null) {
						return;
					}
					for (int i = 0; i < posList.size(); i++) {
						BlockPos pos = posList.get(i);
						if (sl.hasChunkAt(pos)) {
							sl.setBlock(pos, stateList.get(i), DIRECT_FLAGS);
						}
					}
				});
			}
		} else {
			ClientPacketListener conn = mc.getConnection();
			if (conn == null) {
				pasting = false;
				clearPaste();
				return;
			}
			int n = speed();
			while (n-- > 0 && index < runs.size()) {
				Run run = runs.get(index++);
				String block = palette.get(run.id());
				int x = origin.getX() + run.x();
				int y = origin.getY() + run.y();
				int z = origin.getZ() + run.z();
				if (run.len() == 1) {
					conn.sendCommand("setblock " + x + " " + y + " " + z + " " + block);
				} else {
					conn.sendCommand("fill " + x + " " + y + " " + z + " " + (x + run.len() - 1) + " " + y + " " + z + " " + block);
				}
				blocksDone += run.len();
			}
		}

		if (index >= runs.size()) {
			pasting = false;
			KhoaFeature.say(mc, "KhoaCopy: pasted " + blocksDone + " blocks" + (blocksSkipped > 0 ? " (" + blocksSkipped + " skipped: unknown block)" : "") + ".");
			clearPaste();
		}
	}
}
