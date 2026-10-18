package khoa.tools.client.feature.downloadchunk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import it.unimi.dsi.fastutil.shorts.ShortList;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Turns a client-side chunk into the vanilla on-disk format (chunk NBT for
 * region/*.mca and entity NBT for entities/*.mca). Only data the client has is
 * used, so server-only data (chest contents, etc.) is naturally missing.
 */
public final class ChunkExporter {
	private ChunkExporter() {
	}

	public record Result(
		CompoundTag chunk,
		CompoundTag entities,
		int blockEntityCount,
		int entityCount,
		int skippedBlocks,
		int skippedBlockEntities,
		int skippedEntities
	) {
		/** Estimated compressed size in bytes of everything that will be written. */
		public long estimatedSize() {
			return compressedSize(chunk) + (entities == null ? 0 : compressedSize(entities));
		}
	}

	public static boolean isUnknownBlock(BlockState state) {
		return BuiltInRegistries.BLOCK.getResourceKey(state.getBlock()).isEmpty();
	}

	public static Result export(ClientLevel level, LevelChunk chunk) {
		return export(level, chunk, true);
	}

	public static Result export(ClientLevel level, LevelChunk chunk, boolean includeEntities) {
		ChunkPos pos = chunk.getPos();
		RegistryAccess registries = level.registryAccess();

		// ---- sections (all blocks), unknown blocks become air
		LevelChunkSection[] source = chunk.getSections();
		List<SerializableChunkData.SectionData> sections = new ArrayList<>(source.length);
		int skippedBlocks = 0;
		for (int i = 0; i < source.length; i++) {
			LevelChunkSection section = source[i];
			if (section.maybeHas(ChunkExporter::isUnknownBlock)) {
				PalettedContainer<BlockState> copy = section.getStates().copy();
				BlockState air = Blocks.AIR.defaultBlockState();
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							if (isUnknownBlock(copy.get(x, y, z))) {
								copy.set(x, y, z, air);
								skippedBlocks++;
							}
						}
					}
				}
				section = new LevelChunkSection(copy, section.getBiomes());
			}
			sections.add(new SerializableChunkData.SectionData(level.getSectionYFromSectionIndex(i), section, null, null));
		}

		// ---- block entities (sign text/direction, spawners, ...) as the client knows them
		List<CompoundTag> blockEntities = new ArrayList<>();
		int skippedBlockEntities = 0;
		for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
			if (be.isRemoved()
				|| BuiltInRegistries.BLOCK_ENTITY_TYPE.getResourceKey(be.getType()).isEmpty()
				|| isUnknownBlock(be.getBlockState())) {
				skippedBlockEntities++;
				continue;
			}
			try {
				blockEntities.add(be.saveWithFullMetadata(registries));
			} catch (RuntimeException e) {
				skippedBlockEntities++;
			}
		}

		// ---- heightmaps
		Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
		EnumSet<Heightmap.Types> wanted = ChunkStatus.FULL.heightmapsAfter();
		for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
			if (wanted.contains(entry.getKey())) {
				heightmaps.put(entry.getKey(), entry.getValue().getRawData().clone());
			}
		}

		CompoundTag structures = new CompoundTag();
		structures.put("starts", new CompoundTag());
		structures.put("References", new CompoundTag());

		// Light is not exported: the world relights the chunk when it is loaded.
		SerializableChunkData data = new SerializableChunkData(
			level.palettedContainerFactory(),
			pos,
			level.getMinSectionY(),
			level.getGameTime(),
			chunk.getInhabitedTime(),
			ChunkStatus.FULL,
			null,
			null,
			UpgradeData.EMPTY,
			null,
			heightmaps,
			new ChunkAccess.PackedTicks(List.of(), List.of()),
			new ShortList[level.getSectionsCount()],
			false,
			sections,
			List.of(),
			blockEntities,
			structures
		);
		CompoundTag chunkTag = data.write();

		// ---- mobs / entities (not players, not unknown types)
		ListTag entityList = new ListTag();
		int skippedEntities = 0;
		AABB box = new AABB(
			pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(),
			pos.getMaxBlockX() + 1, level.getMinY() + level.getHeight(), pos.getMaxBlockZ() + 1
		);
		for (Entity entity : includeEntities ? level.getEntities((Entity) null, box, e -> true) : List.<Entity>of()) {
			if (entity instanceof Player || entity.isRemoved() || entity.isPassenger()
				|| !entity.chunkPosition().equals(pos)) {
				continue;
			}
			if (!entity.getType().canSerialize()
				|| BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType()).isEmpty()) {
				skippedEntities++;
				continue;
			}
			try {
				TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
				if (entity.save(output)) {
					entityList.add(output.buildResult());
				} else {
					skippedEntities++;
				}
			} catch (RuntimeException e) {
				skippedEntities++;
			}
		}

		CompoundTag entityTag = null;
		if (!entityList.isEmpty()) {
			entityTag = new CompoundTag();
			NbtUtils.addCurrentDataVersion(entityTag);
			entityTag.putIntArray("Position", new int[] {pos.x(), pos.z()});
			entityTag.put("Entities", entityList);
		}

		return new Result(chunkTag, entityTag, blockEntities.size(), entityList.size(),
			skippedBlocks, skippedBlockEntities, skippedEntities);
	}

	private static long compressedSize(CompoundTag tag) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
				NbtIo.write(tag, out);
			}
			return bytes.size();
		} catch (IOException e) {
			return 0;
		}
	}
}
