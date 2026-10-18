package khoa.tools.client.feature.downloadchunk;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.phys.Vec3;

import com.google.gson.JsonObject;

import khoa.tools.KhoaTools;
import khoa.tools.client.config.KhoaConfig;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;

/**
 * /khoatoolsclient loaddownloadedchunk &lt;server/world&gt; - pastes chunks saved by downloadchunk into the
 * current singleplayer world, one at a time, writing blocks straight into the loaded chunk.
 * Order: nearest to where the command was run first, then always the nearest to the last pasted chunk.
 * The player is only teleported when the next chunk is too far from where the player already stands
 * (outside render distance); close chunks are pasted in place. The player is NOT teleported back at the end.
 * far_tp_delay / near_tp_delay are separate saved settings (config/khoatools.json):
 *   /khoatoolsclient loaddownloadedchunk far_tp_delay [ticks]
 *   /khoatoolsclient loaddownloadedchunk near_tp_delay [ticks]
 * /khoatoolsclient loaddownloadedchunk stop cancels.
 */
public final class LoadDownloadedChunkFeature implements KhoaFeature {
	private static final int DEFAULT_FAR_WAIT_TICKS = 20;
	private static final int DEFAULT_NEAR_WAIT_TICKS = 10;
	private static final List<String> FAR_SUGGESTIONS = List.of("0", "10", "20", "40", "60", "100", "200");
	private static final List<String> NEAR_SUGGESTIONS = List.of("0", "2", "5", "10", "20", "40");

	private static final class Job {
		final IntegratedServer server;
		final RegionStore store;
		final ResourceKey<Level> dimension;
		final List<ChunkPos> order;
		final UUID playerId;
		final Vec3 origin;
		final int farWait;
		final int nearWait;
		/** Chunks (Chebyshev) from the player within which no teleport is needed. */
		final int noTpRange;
		final AtomicInteger pasted = new AtomicInteger();
		final AtomicInteger failed = new AtomicInteger();
		volatile boolean busy;
		volatile boolean wasInvulnerable;
		volatile boolean invulnerabilitySaved;
		int index;
		int cooldown;

		Job(IntegratedServer server, RegionStore store, ResourceKey<Level> dimension, List<ChunkPos> order,
			UUID playerId, Vec3 origin, int farWait, int nearWait, int noTpRange) {
			this.server = server;
			this.store = store;
			this.dimension = dimension;
			this.order = order;
			this.playerId = playerId;
			this.origin = origin;
			this.farWait = farWait;
			this.nearWait = nearWait;
			this.noTpRange = noTpRange;
		}
	}

	private Job job;

	@Override
	public String id() {
		return "loaddownloadedchunk";
	}

	@Override
	public String displayName() {
		return "Load Downloaded Chunk";
	}

	@Override
	public String description() {
		return "Singleplayer only. Pastes chunks saved by Chunk Downloader into your current world, nearest first. "
			+ "Type the saved server/world folder name in Source, then click the card to start (click again to stop). "
			+ "You only teleport when the next chunk is out of render distance, and you are not teleported back at the end.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.string("source", "Source", "Folder name inside .minecraft/khoatools/downloadedchunk/ (same names as the command suggestions).",
				() -> KhoaConfig.getString(id(), "source", ""), value -> KhoaConfig.put(id(), "source", value)),
			Setting.integer("far_tp_delay", "Far TP delay (ticks)", "Wait before the next chunk when it is outside render distance. 20 ticks = 1s.",
				0, 24000, LoadDownloadedChunkFeature::farDelay, value -> KhoaConfig.put(id(), "far_tp_delay", value)),
			Setting.integer("near_tp_delay", "Near TP delay (ticks)", "Wait before the next chunk when it is close.",
				0, 24000, LoadDownloadedChunkFeature::nearDelay, value -> KhoaConfig.put(id(), "near_tp_delay", value)));
	}

	@Override
	public boolean isActive() {
		return job != null;
	}

	@Override
	public void activate(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (job != null) {
			abort(mc, "stopped");
			return;
		}
		String source = KhoaConfig.getString(id(), "source", "").trim();
		if (source.isEmpty()) {
			mc.player.sendSystemMessage(Component.literal("LoadDownloadedChunk: type a Source name in Config first").withStyle(ChatFormatting.RED));
			return;
		}
		startJob(mc, source, message -> mc.player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GREEN)),
			message -> mc.player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED)));
	}

	@Override
	public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> abort(null, null));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> abort(null, null));
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("loaddownloadedchunk")
			.then(ClientCommands.literal("stop").executes(this::stopCommand))
			.then(ClientCommands.literal("far_tp_delay")
				.executes(ctx -> showDelay(ctx, "far_tp_delay", farDelay()))
				.then(ClientCommands.argument("ticks", IntegerArgumentType.integer(0))
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FAR_SUGGESTIONS, builder))
					.executes(ctx -> setDelay(ctx, "far_tp_delay", IntegerArgumentType.getInteger(ctx, "ticks")))))
			.then(ClientCommands.literal("near_tp_delay")
				.executes(ctx -> showDelay(ctx, "near_tp_delay", nearDelay()))
				.then(ClientCommands.argument("ticks", IntegerArgumentType.integer(0))
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(NEAR_SUGGESTIONS, builder))
					.executes(ctx -> setDelay(ctx, "near_tp_delay", IntegerArgumentType.getInteger(ctx, "ticks")))))
			.then(ClientCommands.argument("source", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(listSources(), builder))
				.executes(this::start)));
	}

	private static int farDelay() {
		return readDelay("far_tp_delay", DEFAULT_FAR_WAIT_TICKS);
	}

	private static int nearDelay() {
		return readDelay("near_tp_delay", DEFAULT_NEAR_WAIT_TICKS);
	}

	private static int readDelay(String key, int fallback) {
		JsonObject section = KhoaConfig.section("loaddownloadedchunk");
		try {
			return section.has(key) ? Math.max(0, section.get(key).getAsInt()) : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private int showDelay(CommandContext<FabricClientCommandSource> ctx, String key, int value) {
		ctx.getSource().sendFeedback(Component.literal("LoadDownloadedChunk: " + key + " = " + value + " ticks").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private int setDelay(CommandContext<FabricClientCommandSource> ctx, String key, int value) {
		KhoaConfig.section("loaddownloadedchunk").addProperty(key, value);
		KhoaConfig.save();
		ctx.getSource().sendFeedback(Component.literal("LoadDownloadedChunk: " + key + " = " + value + " ticks (saved)").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static List<String> listSources() {
		Path dir = DownloadPaths.root(Minecraft.getInstance());
		List<String> names = new ArrayList<>();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> stream = Files.list(dir)) {
				stream.filter(Files::isDirectory).forEach(p -> names.add(p.getFileName().toString()));
			} catch (IOException e) {
				KhoaTools.LOGGER.error("Could not list downloaded chunk folders", e);
			}
		}
		return names;
	}

	private int stopCommand(CommandContext<FabricClientCommandSource> ctx) {
		if (job == null) {
			ctx.getSource().sendFeedback(Component.literal("LoadDownloadedChunk: nothing is running").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		abort(ctx.getSource().getClient(), "stopped");
		return 1;
	}

	private int start(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft mc = ctx.getSource().getClient();
		String source = StringArgumentType.getString(ctx, "source");
		KhoaConfig.put(id(), "source", source);
		return startJob(mc, source, ctx.getSource()::sendFeedback, ctx.getSource()::sendError) ? 1 : 0;
	}

	/** Shared by the command and the GUI. @return true when the job started. */
	private boolean startJob(Minecraft mc, String source, java.util.function.Consumer<Component> ok, java.util.function.Consumer<Component> error) {
		if (job != null) {
			error.accept(Component.literal("Already running. Use /khoatoolsclient loaddownloadedchunk stop"));
			return false;
		}
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null || mc.player == null) {
			error.accept(Component.literal("Only works in a singleplayer world (it writes straight into the world)."));
			return false;
		}

		Path base = DownloadPaths.root(mc).normalize();
		Path folder = base.resolve(source).normalize();
		if (!folder.startsWith(base) || !Files.isDirectory(folder)) {
			error.accept(Component.literal("No downloaded chunks named '" + source + "'"));
			return false;
		}

		ResourceKey<Level> dimension = mc.level.dimension();
		RegionStore store = new RegionStore(folder, source);
		Set<Long> chunks;
		try {
			chunks = store.listChunks(dimension);
		} catch (IOException e) {
			KhoaTools.LOGGER.error("Could not read downloaded chunks", e);
			closeStore(store);
			error.accept(Component.literal("Could not read downloaded chunks: " + e.getMessage()));
			return false;
		}
		if (chunks.isEmpty()) {
			closeStore(store);
			error.accept(Component.literal("'" + source + "' has no chunks for this dimension"));
			return false;
		}

		int farWait = farDelay();
		int nearWait = nearDelay();
		int noTpRange = Math.max(0, mc.options.getEffectiveRenderDistance() - 1);
		List<ChunkPos> order = sortByProximity(chunks, mc.player.chunkPosition());
		job = new Job(server, store, dimension, order, mc.player.getUUID(), mc.player.position(), farWait, nearWait, noTpRange);
		ok.accept(Component.literal("LoadDownloadedChunk: pasting " + order.size() + " chunks from " + source
			+ " (far_tp_delay " + farWait + ", near_tp_delay " + nearWait + " ticks; no teleport within " + noTpRange
			+ " chunks; /khoatoolsclient loaddownloadedchunk stop to cancel)").withStyle(ChatFormatting.GREEN));
		return true;
	}

	private void tick(Minecraft mc) {
		Job j = job;
		if (j == null || j.busy) {
			return;
		}
		if (mc.level == null || mc.player == null || mc.getSingleplayerServer() != j.server) {
			abort(null, null);
			return;
		}
		if (!mc.level.dimension().equals(j.dimension)) {
			abort(mc, "dimension changed");
			return;
		}
		if (j.index >= j.order.size()) {
			finish(mc, j);
			return;
		}
		if (j.cooldown > 0) {
			j.cooldown--;
			return;
		}

		ChunkPos pos = j.order.get(j.index++);
		if (j.index < j.order.size()) {
			ChunkPos next = j.order.get(j.index);
			int distance = Math.max(Math.abs(next.x() - pos.x()), Math.abs(next.z() - pos.z()));
			j.cooldown = distance > mc.options.getEffectiveRenderDistance() ? j.farWait : j.nearWait;
		}
		j.busy = true;
		mc.gui.setOverlayMessage(Component.literal("Pasting chunk " + j.index + "/" + j.order.size()
			+ " (" + pos.x() + " " + pos.z() + ")").withStyle(ChatFormatting.YELLOW), false);
		j.server.execute(() -> pasteOne(j, pos));
	}

	/** Server thread. */
	private static void pasteOne(Job j, ChunkPos pos) {
		try {
			ServerLevel level = j.server.getLevel(j.dimension);
			if (level == null) {
				throw new IllegalStateException("dimension is not loaded");
			}
			ServerPlayer player = j.server.getPlayerList().getPlayer(j.playerId);
			if (player != null && player.level() == level) {
				if (!j.invulnerabilitySaved) {
					j.wasInvulnerable = player.isInvulnerable();
					j.invulnerabilitySaved = true;
					player.setInvulnerable(true); // pasted blocks can end up around the player
				}
				ChunkPos here = player.chunkPosition();
				int distance = Math.max(Math.abs(here.x() - pos.x()), Math.abs(here.z() - pos.z()));
				if (distance > j.noTpRange) { // close enough chunks are pasted without moving
					player.teleportTo(pos.getMiddleBlockX() + 0.5, player.getY(), pos.getMiddleBlockZ() + 0.5);
					player.setDeltaMovement(Vec3.ZERO);
					player.resetFallDistance();
				}
			}

			CompoundTag tag;
			RegionFile file = j.store.get(j.dimension, RegionStore.REGION, pos);
			try (DataInputStream in = file.getChunkDataInputStream(pos)) {
				if (in == null) {
					throw new IOException("chunk missing in region file");
				}
				tag = NbtIo.read(in);
			}
			ChunkPaster.paste(level, pos, tag);
			int n = j.pasted.incrementAndGet();
			chat(Component.literal("Loaded chunk " + pos.x() + " " + pos.z() + " (" + (pos.x() << 4) + ", " + (pos.z() << 4) + ") ["
				+ n + "/" + j.order.size() + "]").withStyle(ChatFormatting.GREEN));
		} catch (Throwable t) {
			j.failed.incrementAndGet();
			chat(Component.literal("Failed chunk " + pos.x() + " " + pos.z() + " (see log)").withStyle(ChatFormatting.RED));
			KhoaTools.LOGGER.error("Could not paste chunk {}", pos, t);
		} finally {
			j.busy = false;
		}
	}

	/** Any thread: prints a line in the chat on the client thread. */
	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.sendSystemMessage(message);
			}
		});
	}

	private void finish(Minecraft mc, Job j) {
		end(j);
		mc.gui.setOverlayMessage(Component.literal("LoadDownloadedChunk: done, " + j.pasted.get() + " pasted"
			+ (j.failed.get() > 0 ? ", " + j.failed.get() + " failed (see log)" : "")).withStyle(ChatFormatting.GREEN), false);
	}

	/** @param mc null when there is no client to report to; reason null = silent. */
	private void abort(Minecraft mc, String reason) {
		Job j = job;
		if (j == null) {
			return;
		}
		end(j);
		if (mc != null && reason != null) {
			mc.gui.setOverlayMessage(Component.literal("LoadDownloadedChunk: " + reason + " (" + j.pasted.get() + " pasted)")
				.withStyle(ChatFormatting.RED), false);
		}
	}

	/** Close files and drop the temporary invulnerability (the player stays where the last chunk was). */
	private void end(Job j) {
		job = null;
		j.server.execute(() -> {
			try {
				ServerPlayer player = j.server.getPlayerList().getPlayer(j.playerId);
				if (player != null) {
					if (j.invulnerabilitySaved) {
						player.setInvulnerable(j.wasInvulnerable);
					}
				}
			} catch (RuntimeException e) {
				KhoaTools.LOGGER.error("Could not restore player after pasting", e);
			}
			closeStore(j.store);
		});
	}

	private static void closeStore(RegionStore store) {
		for (RegionFile file : store.drain()) {
			try {
				file.close();
			} catch (IOException e) {
				KhoaTools.LOGGER.error("Could not close region file {}", file.getPath(), e);
			}
		}
	}

	/** Nearest to {@code start} first, then each time the nearest remaining chunk to the previous one. */
	static List<ChunkPos> sortByProximity(Set<Long> chunks, ChunkPos start) {
		Set<Long> remaining = new HashSet<>(chunks);
		int minX = start.x(), maxX = start.x(), minZ = start.z(), maxZ = start.z();
		for (long packed : chunks) {
			ChunkPos p = ChunkPos.unpack(packed);
			minX = Math.min(minX, p.x());
			maxX = Math.max(maxX, p.x());
			minZ = Math.min(minZ, p.z());
			maxZ = Math.max(maxZ, p.z());
		}
		int maxRing = Math.max(maxX - minX, maxZ - minZ) + 1;

		List<ChunkPos> out = new ArrayList<>(chunks.size());
		ChunkPos current = start;
		while (!remaining.isEmpty()) {
			ChunkPos best = null;
			long bestDistance = Long.MAX_VALUE;
			for (int ring = 0; ring <= maxRing; ring++) {
				if (best != null && (long) ring * ring > bestDistance) {
					break;
				}
				for (int dx = -ring; dx <= ring; dx++) {
					for (int dz = -ring; dz <= ring; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
							continue;
						}
						int x = current.x() + dx;
						int z = current.z() + dz;
						if (remaining.contains(ChunkPos.pack(x, z))) {
							long d = (long) dx * dx + (long) dz * dz;
							if (d < bestDistance) {
								bestDistance = d;
								best = new ChunkPos(x, z);
							}
						}
					}
				}
			}
			if (best == null) {
				break; // cannot happen, but never loop forever
			}
			remaining.remove(best.pack());
			out.add(best);
			current = best;
		}
		return out;
	}
}
