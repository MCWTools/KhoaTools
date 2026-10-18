package khoa.tools.client.api.pathfinding;

/** Tuning for one pathfinding call. Start from {@link #defaults()} and change what you need. */
public final class PathOptions {
	/** Longest drop allowed, in blocks (3 = no fall damage). */
	public int maxFall = 3;
	/** Move diagonally (never cuts corners). */
	public boolean allowDiagonal = true;
	/** Use ladders, vines and other climbable blocks. */
	public boolean allowClimb = true;
	/** Allow paths through water. */
	public boolean allowSwim = true;
	/**
	 * Weighted A*: 1.0 = always the shortest path, higher = fewer positions searched (faster) and a path that can be a little longer.
	 * 1.2 is nearly always the same path with much less searching.
	 */
	public double heuristicWeight = 1.2;
	/** Give up after this many visited positions. */
	public int maxNodes = 30000;
	/** Give up after this many milliseconds (the search runs on the calling thread). */
	public long maxMillis = 50;
	/** When the goal cannot be reached, return the path to the closest reachable position instead of failing (status stays NO_PATH / LIMIT). */
	public boolean allowPartial = false;

	public static PathOptions defaults() {
		return new PathOptions();
	}

	public PathOptions maxFall(int v) {
		this.maxFall = Math.max(0, v);
		return this;
	}

	public PathOptions allowDiagonal(boolean v) {
		this.allowDiagonal = v;
		return this;
	}

	public PathOptions allowClimb(boolean v) {
		this.allowClimb = v;
		return this;
	}

	public PathOptions allowSwim(boolean v) {
		this.allowSwim = v;
		return this;
	}

	public PathOptions heuristicWeight(double v) {
		this.heuristicWeight = Math.max(1.0, v);
		return this;
	}

	public PathOptions maxNodes(int v) {
		this.maxNodes = Math.max(1, v);
		return this;
	}

	public PathOptions maxMillis(long v) {
		this.maxMillis = Math.max(1, v);
		return this;
	}

	public PathOptions allowPartial(boolean v) {
		this.allowPartial = v;
		return this;
	}
}
