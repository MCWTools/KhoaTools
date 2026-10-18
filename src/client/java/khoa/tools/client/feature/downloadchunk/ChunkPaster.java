package khoa.tools.client.feature.downloadchunk;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Writes a downloaded chunk (vanilla chunk NBT) into a live server level, in place.
 * Must run on the server thread. Only changed blocks are touched, with no neighbour updates,
 * drops or on-place logic, so the result is exactly what was downloaded.
 */
final class ChunkPaster {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

	private ChunkPaster() {
	}

	record Result(int blocks, int blockEntities) {
	}

	static Result paste(ServerLevel level, ChunkPos pos, CompoundTag tag) {
		SerializableChunkData data = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
		LevelChunk chunk = level.getChunk(pos.x(), pos.z()); // loads the chunk if needed
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int changed = 0;
		boolean biomesChanged = false;

		for (SerializableChunkData.SectionData sectionData : data.sectionData()) {
			int sectionY = sectionData.y();
			if (sectionY < level.getMinSectionY() || sectionY > level.getMaxSectionY()) {
				continue;
			}
			LevelChunkSection source = sectionData.chunkSection();
			LevelChunkSection target = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));

			if (!(source.hasOnlyAir() && target.hasOnlyAir())) {
				int baseY = sectionY << 4;
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							BlockState wanted = source.getBlockState(x, y, z);
							if (target.getBlockState(x, y, z) != wanted) {
								cursor.set(minX + x, baseY + y, minZ + z);
								if (level.setBlock(cursor, wanted, FLAGS)) {
									changed++;
								}
							}
						}
					}
				}
			}

			// biomes (4x4x4 cells per section)
			PalettedContainerRO<Holder<Biome>> sourceBiomes = source.getBiomes();
			if (target.getBiomes() instanceof PalettedContainer<Holder<Biome>> targetBiomes) {
				for (int y = 0; y < 4; y++) {
					for (int z = 0; z < 4; z++) {
						for (int x = 0; x < 4; x++) {
							Holder<Biome> wanted = sourceBiomes.get(x, y, z);
							if (targetBiomes.get(x, y, z) != wanted) {
								targetBiomes.set(x, y, z, wanted);
								biomesChanged = true;
							}
						}
					}
				}
			}
		}

		// block entities (sign text, spawners, ...) as the client knew them
		int blockEntities = 0;
		HolderLookup.Provider registries = level.registryAccess();
		for (CompoundTag beTag : data.blockEntities()) {
			int x = beTag.getIntOr("x", Integer.MIN_VALUE);
			int y = beTag.getIntOr("y", Integer.MIN_VALUE);
			int z = beTag.getIntOr("z", Integer.MIN_VALUE);
			if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
				|| (x >> 4) != pos.x() || (z >> 4) != pos.z()) {
				continue;
			}
			BlockPos blockPos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(blockPos);
			BlockEntity entity = level.getBlockEntity(blockPos);
			if (entity == null) {
				entity = BlockEntity.loadStatic(blockPos, state, beTag, registries);
				if (entity == null) {
					continue;
				}
				level.setBlockEntity(entity);
			} else {
				entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, beTag));
			}
			entity.setChanged();
			level.sendBlockUpdated(blockPos, state, state, Block.UPDATE_CLIENTS);
			blockEntities++;
		}

		chunk.markUnsaved();
		if (biomesChanged) {
			level.getChunkSource().chunkMap.resendBiomesForChunks(List.<ChunkAccess>of(chunk));
		}
		return new Result(changed, blockEntities);
	}
}
