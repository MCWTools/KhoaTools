package khoa.tools.client.feature.findleak;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Finds every hole between the room you stand in and the "outside". Runs a little each tick (main thread), no extra thread.
 * <ul>
 * <li>Open cell = no collision (closed doors / trapdoors / gates are walls, open ones are open).</li>
 * <li>Outside = open sky, an unloaded chunk, or the edge of the scan box (max distance). Closed pockets behind an
 * opening (corridors, closets, other rooms) are NOT leaks: they just count as part of the room.</li>
 * <li>Loop: BFS (shortest path) from the player to the nearest outside cell, find the narrowest cross-section on that path
 * (a 2D flood fill on the plane perpendicular to the path, capped), mark it as a hole and seal it virtually, repeat until
 * nothing outside is reachable. Holes that touch each other are merged into one box.</li>
 * </ul>
 */
public final class LeakScanner {
	private static final byte WALL = 1, OPEN = 2, SEALED = 3, UNLOADED = 4;
	private static final byte ROOT = 7;
	private static final int SLICE_CAP = 1024;
	private static final int MAX_HOLES = 300;
	private static final int[] DX = {0, 1, -1, 0, 0, 0, 0};
	private static final int[] DY = {0, 0, 0, 1, -1, 0, 0};
	private static final int[] DZ = {0, 0, 0, 0, 0, 1, -1};
	/** In-plane step directions for a path travelling along axis 0 (x), 1 (y), 2 (z). */
	private static final int[][][] PLANE = {
		{{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}},
		{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}},
		{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}}
	};

	private final ClientLevel level;
	private final int xmin, xmax, ymin, ymax, zmin, zmax, w, h;
	private final int[] stepIdx = new int[7];
	private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
	private byte[] cls, par;
	private int[] stamp, queue = new int[4096];
	private final int[] slice = new int[SLICE_CAP + 8];
	private int stampId, head, tail, escapeIdx = -1, startIdx = -1;
	private boolean escapeSky;
	private int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
	private boolean lastLoaded;

	private final Set<Long> holeCells = new HashSet<>();
	/** Per column (x,z): highest virtually sealed cell. Cells under it no longer see the sky through that column. */
	private final HashMap<Long, Integer> sealedTop = new HashMap<>();
	private int holes, visited;
	private boolean done, truncated, hitLimit;
	private String error;
	private List<AABB> boxes = List.of();

	public LeakScanner(ClientLevel level, int px, int py, int pz, int maxDist) {
		this.level = level;
		this.xmin = px - maxDist;
		this.xmax = px + maxDist;
		this.zmin = pz - maxDist;
		this.zmax = pz + maxDist;
		this.ymin = Math.max(level.getMinY(), py - maxDist);
		this.ymax = Math.min(level.getMinY() + level.getHeight() - 1, py + maxDist);
		this.w = 2 * maxDist + 1;
		this.h = ymax - ymin + 1;
		stepIdx[1] = 1;
		stepIdx[2] = -1;
		stepIdx[3] = w * w;
		stepIdx[4] = -w * w;
		stepIdx[5] = w;
		stepIdx[6] = -w;
		this.cls = new byte[w * w * h];
		this.par = new byte[w * w * h];
		this.stamp = new int[w * w];

		int sy = py;
		byte c = classify(px, sy, pz);
		for (int i = 0; i < 2 && c == WALL; i++) {
			c = classify(px, ++sy, pz);
		}
		if (c == WALL || sy > ymax) {
			fail("Can't start here (you are inside a block).");
			return;
		}
		if (c == UNLOADED || isEscape(px, sy, pz)) {
			fail("You are not inside an enclosed space (this spot is open to the sky or the edge of the loaded world).");
			return;
		}
		startIdx = idx(px, sy, pz);
		startBfs();
	}

	public boolean isDone() {
		return done;
	}

	/** Merged hole boxes (valid when done). */
	public List<AABB> boxes() {
		return boxes;
	}

	/** Number of separate holes found before merging neighbours. */
	public int holes() {
		return holes;
	}

	public boolean truncated() {
		return truncated;
	}

	/** True when some opening leads past the max distance (raise it if that part is inside your room). */
	public boolean hitLimit() {
		return hitLimit;
	}

	/** Non-null when the scan could not run (e.g. standing outside). */
	public String error() {
		return error;
	}

	public int visited() {
		return visited;
	}

	/** Work until the deadline (System.nanoTime). Returns true when the scan is finished. */
	public boolean step(long deadline) {
		while (!done) {
			if (!bfs(deadline)) {
				return false;
			}
			if (escapeIdx < 0) {
				finish();
				break;
			}
			handleEscape();
			if (holes >= MAX_HOLES) {
				truncated = true;
				finish();
				break;
			}
			startBfs();
			if (System.nanoTime() > deadline) {
				return done;
			}
		}
		return done;
	}

	private void fail(String message) {
		error = message;
		done = true;
		release();
	}

	private void release() {
		cls = null;
		par = null;
		stamp = null;
		queue = null;
	}

	// ---------- BFS from the player to the nearest outside cell ----------

	private void startBfs() {
		Arrays.fill(par, (byte) 0);
		head = 0;
		tail = 0;
		escapeIdx = -1;
		par[startIdx] = ROOT;
		queue[tail++] = startIdx;
	}

	private boolean bfs(long deadline) {
		int counter = 0;
		while (head < tail) {
			if ((++counter & 511) == 0 && System.nanoTime() > deadline) {
				return false;
			}
			int cur = queue[head++];
			int x = xmin + cur % w;
			int t = cur / w;
			int z = zmin + t % w;
			int y = ymin + t / w;
			for (int d = 1; d <= 6; d++) {
				// Boundary cells count as outside and end the search, so cur is never on the boundary: neighbours stay inside the box.
				int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
				int ni = cur + stepIdx[d];
				if (par[ni] != 0) {
					continue;
				}
				byte c = cls[ni];
				if (c == 0) {
					c = classify(nx, ny, nz);
					cls[ni] = c;
				}
				if (c == WALL || c == SEALED) {
					continue;
				}
				par[ni] = (byte) d;
				visited++;
				boolean boundary = isBoundary(nx, ny, nz);
				boolean sky = !boundary && c != UNLOADED && isSky(nx, ny, nz);
				if (c == UNLOADED || boundary || sky) {
					escapeIdx = ni;
					escapeSky = sky;
					if (boundary && !sky) {
						hitLimit = true;
					}
					return true;
				}
				if (tail == queue.length) {
					queue = Arrays.copyOf(queue, queue.length * 2);
				}
				queue[tail++] = ni;
			}
		}
		return true;
	}

	/** Box edge or world top/bottom. */
	private boolean isBoundary(int x, int y, int z) {
		return x == xmin || x == xmax || z == zmin || z == zmax || y == ymin || y == ymax;
	}

	/** Nothing but air above this cell (it sees the sky). */
	private boolean isSky(int x, int y, int z) {
		if (y < level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) {
			return false;
		}
		Integer top = sealedTop.get(column(x, z));
		return top == null || top <= y;
	}

	private static long column(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private boolean isEscape(int x, int y, int z) {
		return isBoundary(x, y, z) || isSky(x, y, z);
	}

	private byte classify(int x, int y, int z) {
		int cx = x >> 4, cz = z >> 4;
		if (cx != lastCx || cz != lastCz) {
			lastCx = cx;
			lastCz = cz;
			lastLoaded = level.getChunkSource().getChunkNow(cx, cz) != null;
		}
		if (!lastLoaded) {
			return UNLOADED;
		}
		BlockState state = level.getBlockState(mp.set(x, y, z));
		if (state.isAir()) {
			return OPEN;
		}
		Block block = state.getBlock();
		if ((block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock)
			&& state.getValue(BlockStateProperties.OPEN)) {
			return OPEN;
		}
		return state.getCollisionShape(level, mp).isEmpty() ? OPEN : WALL;
	}

	private int idx(int x, int y, int z) {
		return ((y - ymin) * w + (z - zmin)) * w + (x - xmin);
	}

	// ---------- narrowest cross-section on the path = the hole ----------

	private void handleEscape() {
		int[] rev = new int[64];
		int n = 0;
		int cur = escapeIdx;
		while (true) {
			if (n == rev.length) {
				rev = Arrays.copyOf(rev, n * 2);
			}
			rev[n++] = cur;
			byte d = par[cur];
			if (d == ROOT) {
				break;
			}
			cur -= stepIdx[d];
		}
		// A cell that merely sees the sky can sit far below the real opening (e.g. the floor under a hole in the roof),
		// so for sky escapes the path goes on straight up: that vertical ray passes through the opening.
		int escapeY = ymin + escapeIdx / w / w;
		int ext = escapeSky ? ymax - escapeY : 0;
		int total = n + ext;
		int[] path = new int[total];
		int[] px = new int[total], py = new int[total], pz = new int[total];
		for (int i = 0; i < total; i++) {
			int c = i < n ? rev[n - 1 - i] : escapeIdx + (i - n + 1) * stepIdx[3];
			path[i] = c;
			px[i] = xmin + c % w;
			int t = c / w;
			pz[i] = zmin + t % w;
			py[i] = ymin + t / w;
		}
		n = total;

		// Last minimum wins, so when everything is huge (a whole wall missing) the hole is drawn next to the outside.
		int bestArea = SLICE_CAP + 1;
		int bestI = n - 1;
		for (int i = 0; i < n; i++) {
			int limit = Math.min(bestArea, SLICE_CAP);
			int area = floodSlice(axisAt(px, py, pz, i), path[i], limit);
			if (area <= limit) {
				bestArea = area;
				bestI = i;
			}
		}
		int found = floodSlice(axisAt(px, py, pz, bestI), path[bestI], SLICE_CAP);
		int count = Math.min(found, SLICE_CAP + 1);
		for (int i = 0; i < count; i++) {
			int c = slice[i];
			cls[c] = SEALED;
			int x = xmin + c % w;
			int t = c / w;
			int y = ymin + t / w, z = zmin + t % w;
			holeCells.add(BlockPos.asLong(x, y, z));
			sealedTop.merge(column(x, z), y, Math::max);
		}
		holes++;
	}

	private static int axisAt(int[] px, int[] py, int[] pz, int i) {
		int lo = Math.max(0, i - 3), hi = Math.min(px.length - 1, i + 3);
		int dx = Math.abs(px[hi] - px[lo]), dy = Math.abs(py[hi] - py[lo]), dz = Math.abs(pz[hi] - pz[lo]);
		if (dx >= dy && dx >= dz) {
			return 0;
		}
		return dy >= dz ? 1 : 2;
	}

	private int planeIndex(int axis, int x, int y, int z) {
		return switch (axis) {
			case 0 -> (y - ymin) * w + (z - zmin);
			case 1 -> (z - zmin) * w + (x - xmin);
			default -> (y - ymin) * w + (x - xmin);
		};
	}

	/**
	 * 2D flood fill (4-neighbours) of open cells on the plane perpendicular to {@code axis} through {@code from}.
	 * Cells are collected in {@link #slice}. Stops as soon as more than {@code limit} cells were found; returns the count found.
	 */
	private int floodSlice(int axis, int from, int limit) {
		stampId++;
		int sh = 0, st = 0;
		slice[st++] = from;
		stamp[planeIndex(axis, xmin + from % w, ymin + from / w / w, zmin + from / w % w)] = stampId;
		int[][] dirs = PLANE[axis];
		while (sh < st) {
			int cur = slice[sh++];
			int x = xmin + cur % w;
			int t = cur / w;
			int z = zmin + t % w;
			int y = ymin + t / w;
			for (int[] dir : dirs) {
				int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
				if (nx < xmin || nx > xmax || ny < ymin || ny > ymax || nz < zmin || nz > zmax) {
					continue;
				}
				int pi = planeIndex(axis, nx, ny, nz);
				if (stamp[pi] == stampId) {
					continue;
				}
				int ni = idx(nx, ny, nz);
				byte c = cls[ni];
				if (c == 0) {
					c = classify(nx, ny, nz);
					cls[ni] = c;
				}
				if (c == WALL || c == SEALED) {
					continue;
				}
				stamp[pi] = stampId;
				slice[st++] = ni;
				if (st > limit) {
					return st;
				}
			}
		}
		return st;
	}

	// ---------- merge touching holes into one box ----------

	private void finish() {
		List<AABB> out = new ArrayList<>();
		Set<Long> remaining = new HashSet<>(holeCells);
		ArrayDeque<Long> work = new ArrayDeque<>();
		while (!remaining.isEmpty()) {
			long first = remaining.iterator().next();
			remaining.remove(first);
			work.add(first);
			int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			while (!work.isEmpty()) {
				long c = work.poll();
				int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
				x0 = Math.min(x0, x);
				y0 = Math.min(y0, y);
				z0 = Math.min(z0, z);
				x1 = Math.max(x1, x);
				y1 = Math.max(y1, y);
				z1 = Math.max(z1, z);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							long n = BlockPos.asLong(x + dx, y + dy, z + dz);
							if (remaining.remove(n)) {
								work.add(n);
							}
						}
					}
				}
			}
			out.add(new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1));
		}
		boxes = List.copyOf(out);
		done = true;
		release();
	}
}
