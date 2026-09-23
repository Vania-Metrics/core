package fr.samflix.vaniametrics.core.game;

import java.util.List;

/**
 * What a game server loader (Bukkit, Folia, Sponge...) exposes to the common collectors.
 *
 * <p>Each loader translates its own API into these plain values; the metrics themselves are
 * defined once, in this package. A capability a loader cannot provide is reported as absent
 * (see each method) and the matching metric is simply not published: a missing series is honest,
 * a zero is a lie.
 *
 * <p>When {@link #needsMainThread()} is true, methods are called on the server thread and may read
 * game state directly. Otherwise they are called from a background thread, and the implementation
 * dispatches reads itself (Folia, which has no main thread).
 */
public interface GameServer {

	/** Whether collectors must call this server from the main thread. */
	default boolean needsMainThread() {
		return true;
	}

	int maxPlayers();

	/** TPS over 1, 5 and 15 minutes, or {@code null} when the loader does not compute it. */
	double[] tps();

	/** Average tick duration in milliseconds, or {@code NaN} when unavailable. */
	double averageTickMillis();

	/** Ticks since startup, or {@code -1} when unavailable. */
	int currentTick();

	/**
	 * Durations of the most recent ticks in nanoseconds, most recent last, or an empty array when
	 * the loader does not record them. Must be actual tick work time, not the interval between
	 * ticks: an interval is never below 50 ms and would hide exactly what this measures.
	 */
	long[] recentTickDurations();

	/**
	 * @param withEntityTypes whether to fill {@link WorldSnapshot#entitiesByType()}, which costs a
	 *     pass over every entity
	 */
	List<WorldSnapshot> worlds(boolean withEntityTypes);

	List<PlayerSnapshot> players();
}
