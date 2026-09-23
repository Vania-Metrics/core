package fr.samflix.vaniametrics.core.game;

import java.util.Map;
import java.util.UUID;

/**
 * One online player at collection time.
 *
 * @param brand client brand, {@code null} when unknown or not exposed by the loader
 * @param locale BCP 47 language tag, {@code null} when unknown
 * @param stats vanilla statistics; a missing key counts as zero
 */
public record PlayerSnapshot(
		UUID uuid,
		String name,
		int pingMillis,
		String brand,
		String locale,
		Map<Stat, Long> stats) {

	/** The vanilla statistics the collectors publish. */
	public enum Stat {
		PLAYER_KILLS,
		MOB_KILLS,
		DEATHS,
		/** In tenths of a health point, as vanilla stores it. */
		DAMAGE_DEALT,
		/** In tenths of a health point, as vanilla stores it. */
		DAMAGE_TAKEN,
		JUMPS,
		/** Ticks played (vanilla's misnamed "play_one_minute"). */
		PLAY_TICKS
	}

	public long stat(Stat s) {
		return stats.getOrDefault(s, 0L);
	}
}
