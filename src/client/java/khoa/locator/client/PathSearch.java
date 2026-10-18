package khoa.locator.client;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Weighted A* 3D trên lưới block, chạy từng lát nhỏ mỗi tick (main thread, không dùng thread phụ).
 * Mỗi ô có 3 loại: BLOCKED (có va chạm/chất lỏng/mạng nhện), NARROW (ô trống nhưng khối 3x3x3 quanh nó không trống hết,
 * vẫn chui được 1x1) và WIDE (3x3x3 trống). NARROW đắt gấp NARROW_COST lần nên path ưu tiên chỗ rộng,
 * nhưng vẫn chui chỗ hẹp nếu không có đường khác. Đi qua chỗ hẹp chỉ cho phép đi thẳng theo trục (không lách chéo qua khe).
 * Có tryReuse(): kiểm tra path cũ còn đi được không để khỏi tính lại từ đầu.
 */
public final class PathSearch {
	public static final int MAX_NODES = 40_000;
	private static final double WEIGHT = 1.5;
	private static final double NARROW_COST = 3.0;
	private static final double GOAL_RADIUS_SQ = 9.0;
	private static final int SMOOTH_LOOKAHEAD = 40;
	private static final int NEAR_START = 2; // ô gần chỗ đứng (đang sát đất) không tính là hẹp

	private static final byte BLOCKED = 1, NARROW = 2, WIDE = 3;

	private static final class Node {
		final int x, y, z;
		final byte cls;
		final double h;
		double g, f;
		Node parent;
		boolean closed;
		Node(int x, int y, int z, byte cls, double h) { this.x = x; this.y = y; this.z = z; this.cls = cls; this.h = h; }
	}

	/** narrow[i] = đoạn từ points[i] tới points[i+1] đi qua chỗ hẹp. */
	public record Result(List<Vec3> points, boolean[] narrow, boolean reachedGoal, double remaining, int nodes) {}

	private final Level level;
	private final Vec3 startVec;
	private final int sx, sy, sz, gx, gy, gz;
	private final int minX, maxX, minZ, maxZ, minY, maxY;
	private final Long2ByteOpenHashMap blockCache = new Long2ByteOpenHashMap(); // 1 free, 2 blocked
	private final Long2ByteOpenHashMap cellCache = new Long2ByteOpenHashMap();
	private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
	private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
	private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
	private Node best, goalNode;
	private int expanded = 0;
	private Result result;

	public PathSearch(Level level, Vec3 start, Vec3 goal) {
		this.level = level;
		this.startVec = start;
		this.sx = (int) Math.floor(start.x);
		this.sy = (int) Math.floor(start.y);
		this.sz = (int) Math.floor(start.z);
		this.gx = (int) Math.floor(goal.x);
		this.gy = (int) Math.floor(goal.y);
		this.gz = (int) Math.floor(goal.z);
		int pad = 48;
		this.minX = Math.min(sx, gx) - pad;
		this.maxX = Math.max(sx, gx) + pad;
		this.minZ = Math.min(sz, gz) - pad;
		this.maxZ = Math.max(sz, gz) + pad;
		this.minY = level.getMinY() + 1;
		this.maxY = level.getMaxY() - 1;
		Node s = new Node(sx, Math.max(minY, Math.min(maxY, sy)), sz, WIDE, dist(sx, sy, sz));
		s.f = WEIGHT * s.h;
		nodes.put(BlockPos.asLong(s.x, s.y, s.z), s);
		open.add(s);
		best = s;
	}

	public Result result() { return result; }

	/** Chạy tối đa budget node. Trả true khi xong (kết quả ở result()). */
	public boolean step(int budget) {
		if (result != null) return true;
		while (budget-- > 0) {
			Node cur = open.poll();
			if (cur == null) break;
			if (cur.closed) continue;
			cur.closed = true;
			expanded++;
			if (cur.h < best.h) best = cur;
			if (distSq(cur.x, cur.y, cur.z) <= GOAL_RADIUS_SQ) { goalNode = cur; break; }
			if (expanded >= MAX_NODES) break;
			expand(cur);
		}
		boolean done = goalNode != null || open.isEmpty() || expanded >= MAX_NODES;
		if (done) result = build();
		return done;
	}

	private void expand(Node cur) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					int nx = cur.x + dx, ny = cur.y + dy, nz = cur.z + dz;
					if (nx < minX || nx > maxX || nz < minZ || nz > maxZ || ny < minY || ny > maxY) continue;
					byte cls = cellClass(nx, ny, nz);
					if (cls == BLOCKED) continue;
					boolean narrowMove = cls == NARROW || cur.cls == NARROW;
					if (narrowMove && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue; // hẹp: chỉ đi thẳng trục
					double step = Math.sqrt(dx * dx + dy * dy + dz * dz) * (dy > 0 ? 1.4 : 1.0) * (cls == NARROW ? NARROW_COST : 1.0);
					double g = cur.g + step;
					long key = BlockPos.asLong(nx, ny, nz);
					Node n = nodes.get(key);
					if (n == null || (!n.closed && g < n.g)) {
						Node nn = new Node(nx, ny, nz, cls, n != null ? n.h : dist(nx, ny, nz));
						nn.g = g;
						nn.f = g + WEIGHT * nn.h;
						nn.parent = cur;
						if (n != null) n.closed = true; // lazy delete bản cũ
						nodes.put(key, nn);
						open.add(nn);
					}
				}
			}
		}
	}

	// ---------- phân loại ô ----------

	private boolean blockFree(int x, int y, int z) {
		long key = BlockPos.asLong(x, y, z);
		byte c = blockCache.get(key);
		if (c != 0) return c == 1;
		BlockState st = level.getBlockState(mp.set(x, y, z));
		boolean free = st.getCollisionShape(level, mp).isEmpty() && st.getFluidState().isEmpty() && !st.is(Blocks.COBWEB);
		blockCache.put(key, (byte) (free ? 1 : 2));
		return free;
	}

	private byte cellClass(int x, int y, int z) {
		long key = BlockPos.asLong(x, y, z);
		byte c = cellCache.get(key);
		if (c != 0) return c;
		byte cls;
		if (!blockFree(x, y, z)) cls = BLOCKED;
		else {
			boolean wide = true;
			outer:
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++)
					for (int dz = -1; dz <= 1; dz++)
						if (!blockFree(x + dx, y + dy, z + dz)) { wide = false; break outer; }
			cls = wide ? WIDE : NARROW;
		}
		cellCache.put(key, cls);
		return cls;
	}

	private boolean nearStart(int x, int y, int z) {
		return Math.abs(x - sx) <= NEAR_START && Math.abs(y - sy) <= NEAR_START && Math.abs(z - sz) <= NEAR_START;
	}

	/** Quét đoạn thẳng a->b: -1 nếu bị chặn, ngược lại số mẫu hẹp (mẫu cách 0.5 block). */
	private int scan(int[] a, int[] b) {
		double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 2));
		int narrow = 0;
		for (int k = 0; k <= steps; k++) {
			double t = (double) k / steps;
			int x = (int) Math.round(a[0] + dx * t), y = (int) Math.round(a[1] + dy * t), z = (int) Math.round(a[2] + dz * t);
			if (nearStart(x, y, z)) continue;
			byte c = cellClass(x, y, z);
			if (c == BLOCKED) return -1;
			if (c == NARROW) narrow++;
		}
		return narrow;
	}

	private static boolean axisAligned(int[] a, int[] b) {
		int d = (a[0] != b[0] ? 1 : 0) + (a[1] != b[1] ? 1 : 0) + (a[2] != b[2] ? 1 : 0);
		return d <= 1;
	}

	// ---------- kết quả ----------

	private Result build() {
		Node end = goalNode != null ? goalNode : best;
		List<int[]> raw = new ArrayList<>();
		List<Boolean> rawNarrow = new ArrayList<>();
		for (Node n = end; n != null; n = n.parent) {
			raw.add(new int[]{n.x, n.y, n.z});
			rawNarrow.add(n.cls == NARROW && !nearStart(n.x, n.y, n.z));
		}
		Collections.reverse(raw);
		Collections.reverse(rawNarrow);

		List<int[]> kept = new ArrayList<>();
		int i = 0;
		kept.add(raw.get(0));
		while (i < raw.size() - 1) {
			int next = i + 1;
			for (int j = Math.min(raw.size() - 1, i + SMOOTH_LOOKAHEAD); j > i + 1; j--) {
				int n = scan(raw.get(i), raw.get(j));
				if (n < 0) continue;
				if (n > 0 && !axisAligned(raw.get(i), raw.get(j))) continue; // không lách chéo qua khe
				int rawN = 0;
				for (int k = i; k <= j; k++) if (rawNarrow.get(k)) rawN++;
				if (n > 2 * rawN + 1) continue; // không đổi đường rộng lấy đường tắt hẹp hơn
				next = j;
				break;
			}
			kept.add(raw.get(next));
			i = next;
		}
		return toResult(kept, goalNode != null, Math.sqrt(distSq(end.x, end.y, end.z)), expanded);
	}

	private Result toResult(List<int[]> pts, boolean reached, double remaining, int nodeCount) {
		List<Vec3> out = new ArrayList<>();
		boolean[] narrow = new boolean[Math.max(0, pts.size() - 1)];
		for (int k = 0; k < pts.size(); k++) {
			int[] p = pts.get(k);
			out.add(new Vec3(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5));
			if (k > 0) narrow[k - 1] = scan(pts.get(k - 1), p) > 0;
		}
		return new Result(out, narrow, reached, remaining, nodeCount);
	}

	/**
	 * Tái sử dụng path cũ (đã tới đích): cắt phần đã đi, kiểm tra phần còn lại vẫn đi được.
	 * Rẻ hơn A* rất nhiều. Trả null nếu có đoạn bị chặn -> phải tính lại.
	 */
	public Result tryReuse(List<Vec3> old) {
		if (old == null || old.size() < 2) return null;
		int bestK = 1;
		double bestD = Double.MAX_VALUE;
		for (int k = 1; k < old.size(); k++) {
			double d = distSqToSegment(startVec, old.get(k - 1), old.get(k));
			if (d < bestD) { bestD = d; bestK = k; }
		}
		if (bestD > 16.0) return null; // đã lệch khỏi path quá 4 block
		List<int[]> pts = new ArrayList<>();
		pts.add(new int[]{sx, sy, sz});
		for (int k = bestK; k < old.size(); k++) {
			Vec3 p = old.get(k);
			pts.add(new int[]{(int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)});
		}
		for (int k = 1; k < pts.size(); k++) {
			int n = scan(pts.get(k - 1), pts.get(k));
			if (n < 0) return null;
			if (n > 0 && !axisAligned(pts.get(k - 1), pts.get(k))) return null;
		}
		return toResult(pts, true, 0, 0);
	}

	private static double distSqToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
		return p.distanceToSqr(a.add(ab.scale(t)));
	}

	private double dist(int x, int y, int z) { return Math.sqrt(distSq(x, y, z)); }

	private double distSq(int x, int y, int z) {
		double dx = x - gx, dy = y - gy, dz = z - gz;
		return dx * dx + dy * dy + dz * dz;
	}
}
