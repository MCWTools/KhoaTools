package khoa.tools.client.feature.downloadchunk;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.phys.Vec3;

import khoa.tools.KhoaTools;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;
import khoa.tools.client.util.ServerKey;

/**
 * /khoatoolsclient downloadchunk [allchunkinrenderdistance] - toggle.
 * <ul>
 * <li>default (false): every chunk the player stands in is saved once.</li>
 * <li>allchunkinrenderdistance=true: every loaded chunk in render distance is saved automatically
 * (no entity scan), nearest first, spread over ticks so the game does not freeze.</li>
 * </ul>
 * Files go to .minecraft/khoatools/downloadedchunk/&lt;server or world&gt;/ as vanilla .mca files.
 * While on, chunks that are already saved are tinted green at y=100.
 */
public final class DownloadChunkFeature implements KhoaFeature {
	private static final int OVERLAY_RADIUS = 16;
	private static final double OVERLAY_Y = 100.01;
	private static final double OVERLAY_INSET = 0.1;
	private static final int OVERLAY_COLOR = 0x4D00E64D; // ARGB green
	private static final long ALL_MODE_BUDGET_NANOS = 6_000_000L;
	private static final int ALL_MODE_RESCAN_TICKS = 20;

	private record Key(String dimension, long chunk) {
	}

	private final Set<Key> handled = new HashSet<>();
	private final Map<String, Set<Long>> saved = new HashMap<>();
	private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
	private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "khoatools-downloadchunk-io");
		thread.setDaemon(true);
		return thread;
	});

	private boolean enabled;
	private boolean allChunks;
	private int rescanIn;
	private int savedThisSession;
	private RegionStore store;
	private String storeKey;

	@Override
	public String id() {
		return "downloadchunk";
	}

	@Override
	public String displayName() {
		return "Chunk Downloader";
	}

	@Override
	public String description() {
		return "Saves the chunks you see to .minecraft/khoatools/downloadedchunk/<server or world>/ as vanilla .mca files. "
			+ "Click the card to turn it on/off. Saved chunks are tinted green near you while it is on.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.bool("all_chunks_in_render_distance", "All chunks in render distance",
			"OFF: only the chunk you stand in is saved. ON: every loaded chunk in render distance is saved (nearest first, no lag, entities are not scanned).",
			() -> KhoaConfig.getBool(id(), "all_chunks", false), value -> {
				KhoaConfig.put(id(), "all_chunks", value);
				if (enabled && allChunks != value) {
					allChunks = value;
					pending.clear();
					rescanIn = 0;
				}
			}));
	}

	@Override
	public boolean isActive() {
		return enabled;
	}

	@Override
	public void activate(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (enabled) {
			stop(true);
			mc.player.sendSystemMessage(Component.literal("DownloadChunk: OFF").withStyle(ChatFormatting.RED));
		} else {
			enabled = true;
			allChunks = KhoaConfig.getBool(id(), "all_chunks", false);
			pending.clear();
			rescanIn = 0;
			savedThisSession = 0;
			mc.player.sendSystemMessage(Component.literal("DownloadChunk: ON").withStyle(ChatFormatting.GREEN)
				.append(Component.literal((allChunks ? " (all chunks in render distance)" : " (current chunk)")
					+ " - saving to .minecraft/khoatools/downloadedchunk/" + ServerKey.id(mc)).withStyle(ChatFormatting.GRAY)));
		}
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(true));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop(false));
		LevelRenderEvents.END_EXTRACTION.register(context -> drawOverlay());
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("downloadchunk")
			.executes(ctx -> toggle(ctx, null))
			.then(ClientCommands.argument("allchunkinrenderdistance", BoolArgumentType.bool())
				.executes(ctx -> toggle(ctx, BoolArgumentType.getBool(ctx, "allchunkinrenderdistance")))));
	}

	private int toggle(CommandContext<FabricClientCommandSource> ctx, Boolean allArg) {
		if (enabled) {
			if (allArg != null && allArg != allChunks) {
				allChunks = allArg;
				pending.clear();
				rescanIn = 0;
				ctx.getSource().sendFeedback(Component.literal("DownloadChunk: mode -> "
					+ (allChunks ? "all chunks in render distance" : "current chunk only")).withStyle(ChatFormatting.AQUA));
				return 1;
			}
			stop(true);
			ctx.getSource().sendFeedback(Component.literal("DownloadChunk: OFF").withStyle(ChatFormatting.RED));
		} else {
			enabled = true;
			allChunks = allArg != null ? allArg : KhoaConfig.getBool(id(), "all_chunks", false);
			pending.clear();
			rescanIn = 0;
			savedThisSession = 0;
			ctx.getSource().sendFeedback(Component.literal("DownloadChunk: ON")
				.withStyle(ChatFormatting.GREEN)
				.append(Component.literal((allChunks ? " (all chunks in render distance)" : " (current chunk)")
					+ " - saving to .minecraft/khoatools/downloadedchunk/" + ServerKey.id(ctx.getSource().getClient()))
					.withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	/** Disable and close region files after the pending writes are done. */
	private void stop(boolean disable) {
		if (disable) {
			enabled = false;
		}
		handled.clear();
		saved.clear();
		pending.clear();
		if (store != null) {
			List<RegionFile> files = store.drain();
			store = null;
			storeKey = null;
			io.execute(() -> {
				for (RegionFile file : files) {
					try {
						file.close();
					} catch (IOException e) {
						KhoaTools.LOGGER.error("Could not close region file {}", file.getPath(), e);
					}
				}
			});
		}
	}

	private static String dimensionId(ClientLevel level) {
		return level.dimension().identifier().toString();
	}

	private void ensureStore(Minecraft mc) {
		String id = ServerKey.id(mc);
		if (store == null || !id.equals(storeKey)) {
			if (store != null) {
				stop(false);
			}
			store = new RegionStore(DownloadPaths.root(mc).resolve(id), id);
			storeKey = id;
		}
	}

	/** Chunks already on disk for this dimension (loaded once, then kept up to date in memory). */
	private Set<Long> savedSet(ClientLevel level) throws IOException {
		String dim = dimensionId(level);
		Set<Long> set = saved.get(dim);
		if (set == null) {
			set = store.listChunks(level.dimension());
			saved.put(dim, set);
		}
		return set;
	}

	private void tick(Minecraft mc) {
		if (!enabled || mc.level == null || mc.player == null) {
			return;
		}
		try {
			ensureStore(mc);
			if (allChunks) {
				tickAll(mc, mc.level);
			} else {
				tickCurrent(mc, mc.level);
			}
		} catch (Exception e) {
			KhoaTools.LOGGER.error("DownloadChunk tick failed", e);
		}
	}

	private void tickCurrent(Minecraft mc, ClientLevel level) {
		ChunkPos pos = mc.player.chunkPosition();
		Key key = new Key(dimensionId(level), pos.pack());
		if (handled.contains(key)) {
			return;
		}
		LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
		if (chunk == null || chunk.isEmpty()) {
			return; // not received yet, try again next tick
		}
		handled.add(key);
		try {
			download(mc, level, chunk, pos, true);
		} catch (Exception e) {
			KhoaTools.LOGGER.error("Could not download chunk {}", pos, e);
			mc.gui.setOverlayMessage(Component.literal("Failed to download chunk (" + pos.x() + " " + pos.z() + ")")
				.withStyle(ChatFormatting.RED), false);
		}
	}

	private void tickAll(Minecraft mc, ClientLevel level) throws IOException {
		String dim = dimensionId(level);
		if (--rescanIn <= 0) {
			rebuildPending(mc, level, dim);
			rescanIn = ALL_MODE_RESCAN_TICKS;
		}
		long deadline = System.nanoTime() + ALL_MODE_BUDGET_NANOS;
		while (!pending.isEmpty() && System.nanoTime() < deadline) {
			ChunkPos pos = pending.poll();
			Key key = new Key(dim, pos.pack());
			if (handled.contains(key)) {
				continue;
			}
			LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
			if (chunk == null || chunk.isEmpty()) {
				continue;
			}
			handled.add(key);
			try {
				download(mc, level, chunk, pos, false);
			} catch (Exception e) {
				KhoaTools.LOGGER.error("Could not download chunk {}", pos, e);
			}
		}
		mc.gui.setOverlayMessage(Component.literal("DownloadChunk (all): " + savedThisSession + " saved, " + pending.size() + " queued")
			.withStyle(ChatFormatting.YELLOW), false);
	}

	/** Every loaded chunk inside render distance that is neither saved nor already handled, nearest first. */
	private void rebuildPending(Minecraft mc, ClientLevel level, String dim) throws IOException {
		pending.clear();
		Set<Long> done = savedSet(level);
		ChunkPos center = mc.player.chunkPosition();
		int radius = mc.options.getEffectiveRenderDistance();
		List<ChunkPos> candidates = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = center.x() + dx;
				int z = center.z() + dz;
				long packed = ChunkPos.pack(x, z);
				if (done.contains(packed) || handled.contains(new Key(dim, packed))) {
					continue;
				}
				if (level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null) {
					candidates.add(new ChunkPos(x, z));
				}
			}
		}
		candidates.sort(Comparator.comparingLong(p -> {
			long dx = p.x() - center.x();
			long dz = p.z() - center.z();
			return dx * dx + dz * dz;
		}));
		pending.addAll(candidates);
	}

	private void download(Minecraft mc, ClientLevel level, LevelChunk chunk, ChunkPos pos, boolean includeEntities) throws IOException {
		RegionFile regionFile = store.get(level.dimension(), RegionStore.REGION, pos);
		boolean exists;
		synchronized (regionFile) {
			exists = regionFile.hasChunk(pos);
		}
		Set<Long> done = savedSet(level);
		if (exists) {
			done.add(pos.pack());
			return; // already downloaded before, skip
		}

		ChunkExporter.Result result = ChunkExporter.export(level, chunk, includeEntities);
		long estimated = result.estimatedSize();
		if (includeEntities) {
			mc.gui.setOverlayMessage(Component.literal("Downloading chunk (" + pos.x() + " " + pos.z() + ") ")
				.withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("~" + formatSize(estimated)).withStyle(ChatFormatting.GRAY)), false);
		}

		RegionFile entityFile = result.entities() == null ? null : store.get(level.dimension(), RegionStore.ENTITIES, pos);
		CompoundTag chunkTag = result.chunk();
		CompoundTag entityTag = result.entities();
		done.add(pos.pack());
		savedThisSession++;

		io.execute(() -> {
			try {
				write(regionFile, pos, chunkTag);
				if (entityFile != null) {
					write(entityFile, pos, entityTag);
				}
				if (includeEntities) {
					mc.execute(() -> mc.gui.setOverlayMessage(
						Component.literal("Downloaded chunk (" + pos.x() + " " + pos.z() + ") " + formatSize(estimated))
							.withStyle(ChatFormatting.GREEN), false));
				}
			} catch (IOException e) {
				KhoaTools.LOGGER.error("Could not write chunk {}", pos, e);
				mc.execute(() -> mc.gui.setOverlayMessage(
					Component.literal("Failed to save chunk (" + pos.x() + " " + pos.z() + ")")
						.withStyle(ChatFormatting.RED), false));
			}
		});
	}

	/** Green 2D layer at y=100 over every chunk that is already saved, so you do not walk over it again. */
	private void drawOverlay() {
		if (!enabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		Set<Long> done = saved.get(dimensionId(mc.level));
		if (done == null || done.isEmpty()) {
			return;
		}
		ChunkPos center = mc.player.chunkPosition();
		GizmoStyle style = GizmoStyle.fill(OVERLAY_COLOR);
		for (int dx = -OVERLAY_RADIUS; dx <= OVERLAY_RADIUS; dx++) {
			for (int dz = -OVERLAY_RADIUS; dz <= OVERLAY_RADIUS; dz++) {
				int x = center.x() + dx;
				int z = center.z() + dz;
				if (!done.contains(ChunkPos.pack(x, z))) {
					continue;
				}
				double x0 = (x << 4) + OVERLAY_INSET;
				double x1 = ((x + 1) << 4) - OVERLAY_INSET;
				double z0 = (z << 4) + OVERLAY_INSET;
				double z1 = ((z + 1) << 4) - OVERLAY_INSET;
				Gizmos.rect(new Vec3(x0, OVERLAY_Y, z0), new Vec3(x1, OVERLAY_Y, z0),
					new Vec3(x1, OVERLAY_Y, z1), new Vec3(x0, OVERLAY_Y, z1), style);
			}
		}
	}

	private static void write(RegionFile file, ChunkPos pos, CompoundTag tag) throws IOException {
		try (DataOutputStream out = file.getChunkDataOutputStream(pos)) {
			NbtIo.write(tag, out);
		}
		file.flush();
	}

	private static String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		}
		return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
	}
}
