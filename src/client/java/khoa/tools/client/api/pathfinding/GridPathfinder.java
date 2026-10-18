package khoa.tools.client.api.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* over standing positions (the cell of the feet). Moves: walk, diagonal (no corner cutting), step up 1 block,
 * drop up to maxFall, climb ladders/vines, swim. Positions are returned as int[]{x, y, z} (feet cell).
 */
public final class GridPathfinder {
	public enum Status {
		FOUND, START_BLOCKED, END_BLOCKED, NO_PATH, LIMIT
	}

	public static final class Result {
		public final Status status;
		public final List<int[]> path;
		public final int visited;
		public final long millis;

		Result(Status status, List<int[]> path, int visited, long millis) {
			this.status = status;
			this.path = path;
			this.visited = visited;
			this.millis = millis;
		}
	}

	private static final class Node implements Comparable<Node> {
		final int x, y, z;
		double g, f;
		Node parent;
		boolean closed;

		Node(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public int compareTo(Node o) {
			return Double.compare(f, o.f);
		}
	}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] DIAGS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private final Terrain t;
	private final PathOptions o;
	private final Map<Long, Node> nodes = new HashMap<>();
	private final PriorityQueue<Node> open = new PriorityQueue<>();
	private int ex, ey, ez;

	private GridPathfinder(Terrain t, PathOptions o) {
		this.t = t;
		this.o = o;
	}

	public static Result find(Terrain terrain, int sx, int sy, int sz, int ex, int ey, int ez, PathOptions options) {
		return new GridPathfinder(terrain, options).run(sx, sy, sz, ex, ey, ez);
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	private boolean canStand(int x, int y, int z) {
		if (!t.loaded(x, y, z) || !t.loaded(x, y + 1, z)) {
			return false;
		}
		if (!t.clear(x, y, z) || !t.clear(x, y + 1, z)) {
			return false;
		}
		boolean wet = t.water(x, y, z);
		if (!o.allowSwim && (wet || t.water(x, y + 1, z))) {
			return false;
		}
		return t.solidTop(x, y - 1, z) || (o.allowClimb && t.climbable(x, y, z)) || (o.allowSwim && wet);
	}

	private boolean bodyClear(int x, int y, int z) {
		return t.loaded(x, y, z) && t.clear(x, y, z);
	}

	private double h(int x, int y, int z) {
		double dx = Math.abs(x - ex), dz = Math.abs(z - ez), dy = Math.abs(y - ey);
		double straight = Math.abs(dx - dz), diag = Math.min(dx, dz);
		return ((straight + diag * 1.4142) + dy) * o.heuristicWeight;
	}

	private Result run(int sx, int sy, int sz, int gx, int gy, int gz) {
		long begin = System.nanoTime();
		this.ex = gx;
		this.ey = gy;
		this.ez = gz;

		// the start may be a little above the floor (jumping, standing on a slab): settle it down
		int startY = sy;
		while (!canStand(sx, startY, sz) && startY > sy - 4 && bodyClear(sx, startY, sz)) {
			startY--;
		}
		if (!canStand(sx, startY, sz)) {
			return new Result(Status.START_BLOCKED, List.of(), 0, ms(begin));
		}
		if (!canStand(gx, gy, gz)) {
			return new Result(Status.END_BLOCKED, List.of(), 0, ms(begin));
		}

		Node start = node(sx, startY, sz);
		start.g = 0;
		start.f = h(sx, startY, sz);
		open.add(start);
		Node best = start;
		double bestH = start.f / o.heuristicWeight;
		int visited = 0;
		boolean limit = false;

		while (!open.isEmpty()) {
			Node cur = open.poll();
			if (cur.closed) {
				continue;
			}
			cur.closed = true;
			visited++;
			if (cur.x == gx && cur.y == gy && cur.z == gz) {
				return new Result(Status.FOUND, build(cur), visited, ms(begin));
			}
			double ch = h(cur.x, cur.y, cur.z) / o.heuristicWeight;
			if (ch < bestH) {
				bestH = ch;
				best = cur;
			}
			if (visited >= o.maxNodes || ((visited & 127) == 0 && ms(begin) >= o.maxMillis)) {
				limit = true;
				break;
			}
			expand(cur);
		}
		Status status = limit ? Status.LIMIT : Status.NO_PATH;
		List<int[]> partial = o.allowPartial && best != start ? build(best) : List.of();
		return new Result(status, partial, visited, ms(begin));
	}

	private static long ms(long begin) {
		return (System.nanoTime() - begin) / 1_000_000L;
	}

	private Node node(int x, int y, int z) {
		return nodes.computeIfAbsent(key(x, y, z), k -> new Node(x, y, z));
	}

	private void relax(Node from, int x, int y, int z, double cost) {
		Node n = node(x, y, z);
		if (n.closed) {
			return;
		}
		double g = from.g + cost;
		if (n.parent == null || g < n.g) {
			n.g = g;
			n.f = g + h(x, y, z);
			n.parent = from;
			open.add(n);
		}
	}

	private void expand(Node c) {
		int x = c.x, y = c.y, z = c.z;
		boolean inWater = t.water(x, y, z);

		for (int[] d : DIRS) {
			int tx = x + d[0], tz = z + d[1];
			if (canStand(tx, y, tz)) {
				relax(c, tx, y, tz, 1.0);
			} else if (canStand(tx, y + 1, tz) && bodyClear(x, y + 2, z)) {
				relax(c, tx, y + 1, tz, 2.0); // jump up one block
			} else if (bodyClear(tx, y, tz) && bodyClear(tx, y + 1, tz)) {
				for (int k = 1; k <= o.maxFall; k++) { // walk off an edge and fall
					if (canStand(tx, y - k, tz)) {
						relax(c, tx, y - k, tz, 1.0 + 0.5 * k);
						break;
					}
					if (!bodyClear(tx, y - k, tz)) {
						break;
					}
				}
			}
		}

		if (o.allowDiagonal) {
			for (int[] d : DIAGS) {
				int tx = x + d[0], tz = z + d[1];
				if (canStand(tx, y, tz)
					&& bodyClear(x + d[0], y, z) && bodyClear(x + d[0], y + 1, z)
					&& bodyClear(x, y, z + d[1]) && bodyClear(x, y + 1, z + d[1])) {
					relax(c, tx, y, tz, 1.4142);
				}
			}
		}

		if (o.allowClimb) {
			boolean onClimb = t.climbable(x, y, z);
			if (onClimb && canStand(x, y + 1, z)) {
				relax(c, x, y + 1, z, 1.5);
			}
			if ((onClimb || t.climbable(x, y - 1, z)) && canStand(x, y - 1, z)) {
				relax(c, x, y - 1, z, 1.5);
			}
		}

		if (o.allowSwim && inWater) {
			if (canStand(x, y + 1, z)) {
				relax(c, x, y + 1, z, 1.0);
			}
			if (canStand(x, y - 1, z)) {
				relax(c, x, y - 1, z, 1.0);
			}
		}
	}

	private List<int[]> build(Node end) {
		List<int[]> out = new ArrayList<>();
		for (Node n = end; n != null; n = n.parent) {
			out.add(new int[] {n.x, n.y, n.z});
		}
		Collections.reverse(out);
		return out;
	}
}
