package khoa.tools.client.api;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import khoa.tools.client.api.pathfinding.GridPathfinder;
import khoa.tools.client.api.pathfinding.PathOptions;
import khoa.tools.client.api.pathfinding.Terrain;

/**
 * Pathfinding for other mods. Call from the client thread (it reads the loaded world, it does not touch the network).
 *
 * <pre>{@code
 * KhoaPathfinding.PathResult r = KhoaPathfinding.findPath(player.blockPosition(), new BlockPos(100, 64, -20));
 * if (r.found()) {
 *     for (BlockPos p : r.path()) { ... } // feet positions, first = start, last = end
 * }
 * }</pre>
 *
 * A "position" is the block the feet are in. Moves: walk, diagonal (no corner cutting), jump up 1 block, drop up to
 * {@code maxFall}, climb ladders / vines, swim. Water, cobweb, powder snow, honey, soul sand and the like are treated as
 * normal blocks. Avoided: lava, fire, campfires, magma blocks, cactus, sweet berry bushes, wither roses.
 * Unloaded chunks are never entered.
 */
public final class KhoaPathfinding {
	private KhoaPathfinding() {
	}

	public enum Status {
		/** A path to the end was found. */
		FOUND,
		/** The start cannot be stood on (inside a block, in lava, nothing below it...). */
		START_BLOCKED,
		/** The end cannot be stood on. */
		END_BLOCKED,
		/** Every reachable position was searched: the end cannot be reached. */
		NO_PATH,
		/** Stopped by maxNodes / maxMillis before an answer. */
		LIMIT,
		/** Not in a world. */
		NO_WORLD
	}

	/**
	 * @param status  FOUND, or why not
	 * @param path    feet positions from start to end when FOUND; empty otherwise (or the way to the closest position when
	 *                {@link PathOptions#allowPartial} is on and the end was not reached)
	 * @param visited positions searched
	 * @param millis  time spent
	 */
	public record PathResult(Status status, List<BlockPos> path, int visited, long millis) {
		/** True = a complete path to the end exists. */
		public boolean found() {
			return status == Status.FOUND;
		}

		/** Block centre of every step, ready to walk to. */
		public List<Vec3> centers() {
			List<Vec3> out = new ArrayList<>(path.size());
			for (BlockPos p : path) {
				out.add(Vec3.atBottomCenterOf(p));
			}
			return out;
		}
	}

	/** Path in the world the client is in. */
	public static PathResult findPath(BlockPos start, BlockPos end) {
		return findPath(start, end, PathOptions.defaults());
	}

	public static PathResult findPath(BlockPos start, BlockPos end, PathOptions options) {
		LevelReader level = Minecraft.getInstance().level;
		if (level == null) {
			return new PathResult(Status.NO_WORLD, List.of(), 0, 0);
		}
		return findPath(level, start, end, options);
	}

	public static PathResult findPath(int sx, int sy, int sz, int ex, int ey, int ez) {
		return findPath(new BlockPos(sx, sy, sz), new BlockPos(ex, ey, ez), PathOptions.defaults());
	}

	/** Positions are the feet: pass {@code BlockPos.containing(entity.position())}. */
	public static PathResult findPath(Vec3 start, Vec3 end) {
		return findPath(BlockPos.containing(start), BlockPos.containing(end), PathOptions.defaults());
	}

	/** Path in any world you give it (for example a ClientLevel or a ServerLevel). */
	public static PathResult findPath(LevelReader level, BlockPos start, BlockPos end, PathOptions options) {
		GridPathfinder.Result r = GridPathfinder.find(new LevelTerrain(level), start.getX(), start.getY(), start.getZ(),
			end.getX(), end.getY(), end.getZ(), options);
		List<BlockPos> path = new ArrayList<>(r.path.size());
		for (int[] p : r.path) {
			path.add(new BlockPos(p[0], p[1], p[2]));
		}
		Status status = switch (r.status) {
			case FOUND -> Status.FOUND;
			case START_BLOCKED -> Status.START_BLOCKED;
			case END_BLOCKED -> Status.END_BLOCKED;
			case NO_PATH -> Status.NO_PATH;
			case LIMIT -> Status.LIMIT;
		};
		return new PathResult(status, List.copyOf(path), r.visited, r.millis);
	}

	/** Shortcut: is there a path at all? */
	public static boolean canReach(BlockPos start, BlockPos end) {
		return findPath(start, end).found();
	}

	/** Reads blocks straight from the level without allocating a BlockPos per lookup. */
	private static final class LevelTerrain implements Terrain {
		private final LevelReader level;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		LevelTerrain(LevelReader level) {
			this.level = level;
		}

		private BlockState state(int x, int y, int z) {
			pos.set(x, y, z);
			return level.getBlockState(pos);
		}

		private static boolean dangerous(BlockState s) {
			return s.getFluidState().is(FluidTags.LAVA)
				|| s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.WITHER_ROSE)
				|| s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES);
		}

		@Override
		public boolean loaded(int x, int y, int z) {
			if (y < level.getMinY() || y > level.getMaxY()) {
				return false;
			}
			pos.set(x, y, z);
			return level.hasChunkAt(pos);
		}

		@Override
		public boolean clear(int x, int y, int z) {
			BlockState s = state(x, y, z);
			if (dangerous(s)) {
				return false;
			}
			if (s.is(BlockTags.CLIMBABLE)) {
				return true; // ladders have a thin collision box but you walk "into" them
			}
			VoxelShape shape = s.getCollisionShape(level, pos);
			return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.13; // carpets and the like do not block
		}

		@Override
		public boolean solidTop(int x, int y, int z) {
			BlockState s = state(x, y, z);
			if (dangerous(s)) {
				return false;
			}
			VoxelShape shape = s.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				return false;
			}
			double top = shape.max(Direction.Axis.Y);
			return top >= 0.5 && top <= 1.0 + 1.0E-6; // slabs yes, fences (1.5) no
		}

		@Override
		public boolean climbable(int x, int y, int z) {
			BlockState s = state(x, y, z);
			return s.is(BlockTags.CLIMBABLE) && !dangerous(s);
		}

		@Override
		public boolean water(int x, int y, int z) {
			return state(x, y, z).getFluidState().is(FluidTags.WATER);
		}
	}
}
