package fr.samflix.vaniametrics.core.game;

import java.util.Map;

/**
 * One world at collection time.
 *
 * @param entities {@code -1} when the loader cannot count them
 * @param tileEntities {@code -1} when the loader cannot count them
 * @param chunks {@code -1} when the loader cannot count them
 * @param weather 0 = clear, 1 = rain, 2 = thunder
 * @param entitiesByType lowercase entity type to count; empty when not requested
 */
public record WorldSnapshot(
		String name,
		int entities,
		int tileEntities,
		int chunks,
		int players,
		long fullTime,
		int weather,
		Map<String, Integer> entitiesByType) {
}
