package khoa.tools.client.api.pathfinding;

/** What the pathfinder needs to know about the world. Pure Java so the algorithm can be tested without Minecraft. */
public interface Terrain {
	/** False when the chunk is not loaded (unknown space is never walked through). */
	boolean loaded(int x, int y, int z);

	/** A body can be in this cell: no solid collision, no danger (lava, fire, magma, cactus, cobweb...). Water counts as clear. */
	boolean clear(int x, int y, int z);

	/** The top of this cell can be stood on (a floor) and is not dangerous. */
	boolean solidTop(int x, int y, int z);

	boolean climbable(int x, int y, int z);

	boolean water(int x, int y, int z);
}
