package khoa.logoutghost.client.ghost;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks logouts and owns every ghost. All methods run on the client main thread.
 */
public final class GhostManager {
	/** A player entity removed this recently (ticks) before their logout packet still counts as "in range". */
	private static final int REMOVED_WINDOW_TICKS = 6;
	private static final int MAX_GHOSTS = 64;

	private record Removed(GhostSnapshot snapshot, long tick) {}

	private static final Map<UUID, Removed> recentlyRemoved = new HashMap<>();
	private static final Map<Integer, GhostPlayer> ghostsById = new HashMap<>();
	private static final List<GhostPlayer> ghostList = new ArrayList<>();

	private static ClientLevel trackedLevel = null;
	private static long tickCounter = 0;
	/** Client-only entity ids are negative so they can never collide with server entity ids. */
	private static int nextGhostId = -1_000_000;

	private GhostManager() {}

	// ---------------------------------------------------------------- packet hooks

	/** Called before ClientboundRemoveEntitiesPacket is applied: remember players that are about to vanish. */
	public static void onEntitiesRemoved(IntList ids) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || GhostConfig.get().fadeTicks == 0) {
			return;
		}
		for (int i = 0; i < ids.size(); i++) {
			Entity e = level.getEntity(ids.getInt(i));
			if (e instanceof RemotePlayer rp && !(e instanceof GhostPlayer)) {
				recentlyRemoved.put(rp.getUUID(), new Removed(GhostSnapshot.capture(rp), tickCounter));
			}
		}
	}

	/** Called before ClientboundPlayerInfoRemovePacket is applied: these players left the server. */
	public static void onPlayersLogout(List<UUID> uuids) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || GhostConfig.get().fadeTicks == 0) {
			return;
		}
		syncLevel(level);
		for (UUID uuid : uuids) {
			if (uuid.equals(mc.player.getUUID())) {
				continue;
			}
			GhostSnapshot snap = null;
			Player inLevel = level.getPlayerByUUID(uuid);
			if (inLevel instanceof RemotePlayer rp && !(inLevel instanceof GhostPlayer)) {
				snap = GhostSnapshot.capture(rp);
			} else {
				Removed r = recentlyRemoved.get(uuid);
				if (r != null && tickCounter - r.tick() <= REMOVED_WINDOW_TICKS) {
					snap = r.snapshot();
				}
			}
			recentlyRemoved.remove(uuid);
			if (snap != null) {
				spawn(level, snap);
			}
		}
	}

	// ---------------------------------------------------------------- lifecycle

	private static void spawn(ClientLevel level, GhostSnapshot snap) {
		GhostConfig cfg = GhostConfig.get();
		while (ghostList.size() >= MAX_GHOSTS) {
			remove(ghostList.get(0));
		}
		GhostPlayer ghost = new GhostPlayer(level, snap, cfg.fadeTicks, cfg.baseAlpha());
		ghost.setId(nextGhostId--);
		level.addEntity(ghost);
		ghostsById.put(ghost.getId(), ghost);
		ghostList.add(ghost);
	}

	private static void remove(GhostPlayer ghost) {
		ghostList.remove(ghost);
		ghostsById.remove(ghost.getId());
		ClientLevel level = trackedLevel;
		if (level != null) {
			level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Removes every ghost right now. Returns how many there were. */
	public static int clearAll() {
		int n = ghostList.size();
		for (GhostPlayer g : new ArrayList<>(ghostList)) {
			remove(g);
		}
		ghostList.clear();
		ghostsById.clear();
		recentlyRemoved.clear();
		return n;
	}

	/** Forget everything without touching the (already gone) level. */
	private static void forgetAll() {
		ghostList.clear();
		ghostsById.clear();
		recentlyRemoved.clear();
	}

	private static void syncLevel(ClientLevel level) {
		if (trackedLevel != level) {
			forgetAll();
			trackedLevel = level;
		}
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			forgetAll();
			trackedLevel = null;
			return;
		}
		syncLevel(level);
		if (mc.isPaused()) {
			return;
		}
		tickCounter++;

		recentlyRemoved.values().removeIf(r -> tickCounter - r.tick() > REMOVED_WINDOW_TICKS);

		Iterator<GhostPlayer> it = ghostList.iterator();
		List<GhostPlayer> finished = null;
		while (it.hasNext()) {
			GhostPlayer g = it.next();
			g.ghostAge++;
			if (g.isFinished() || g.isRemoved()) {
				if (finished == null) {
					finished = new ArrayList<>();
				}
				finished.add(g);
			}
		}
		if (finished != null) {
			for (GhostPlayer g : finished) {
				remove(g);
			}
		}
	}

	// ---------------------------------------------------------------- render hooks

	public static boolean isGhostId(int entityId) {
		return ghostsById.containsKey(entityId);
	}

	/** Alpha for the ghost with this entity id, or -1 if it is not a ghost. */
	public static float alphaFor(int entityId, float partialTick) {
		GhostPlayer g = ghostsById.get(entityId);
		return g == null ? -1.0F : g.alpha(partialTick);
	}

	/** Called every frame from DebugRenderer.emitGizmos: outline each ghost's hitbox. */
	public static void emitHitboxes(float partialTick) {
		if (!GhostConfig.get().showHitbox || ghostList.isEmpty()) {
			return;
		}
		for (GhostPlayer g : ghostList) {
			int a = Math.max(0, Math.min(255, Math.round(g.fadeFactor(partialTick) * 255.0F)));
			if (a == 0) {
				continue;
			}
			Gizmos.cuboid(g.getBoundingBox(), GizmoStyle.stroke((a << 24) | 0x00FFFFFF));
		}
	}

	public static int count() {
		return ghostList.size();
	}
}
