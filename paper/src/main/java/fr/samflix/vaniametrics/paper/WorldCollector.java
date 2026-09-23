package fr.samflix.vaniametrics.paper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Worlds: entities, tile entities, chunks.
 *
 * <p>Built on three O(1) counters. Paper exposes {@code getEntityCount()},
 * {@code getTileEntityCount()} and {@code getChunkCount()}, counters the server already keeps.
 * Most exporters call {@code getEntities().size()}, which builds a list of every entity in the
 * world on each scrape; on a busy server the exporter becomes a source of lag itself.
 *
 * <p>The per-type breakdown is necessarily O(n), since every entity has to be looked at. So it
 * runs in the background, and it is bounded: only types above a threshold are published.
 * Otherwise a hundred entity types across three worlds would make three hundred time series
 * counting zeros.
 */
final class WorldCollector implements Collector {

	private final int typeThreshold;

	private Gauge entities;
	private Gauge tileEntities;
	private Gauge chunks;
	private Gauge players;
	private Gauge byType;
	private Gauge worldTime;
	private Gauge weather;

	WorldCollector(Config config) {
		this.typeThreshold = config.getInt("collector.world.entity_type_threshold", 5);
	}

	@Override
	public String name() {
		return "world";
	}

	@Override
	public boolean needsMainThread() {
		return true;
	}

	@Override
	public boolean isBackground() {
		// Because of the per-type breakdown alone. The rest would fit on scrape, but splitting would
		// make two collectors for one subject, and ten seconds of delay on an entity count has
		// never changed a diagnosis.
		return true;
	}

	@Override
	public long intervalSeconds() {
		return 15;
	}

	@Override
	public void declare(MetricRegistry r) {
		entities = r.gauge("world_entities", "Loaded entities. O(1) server counter.", "world");
		tileEntities = r.gauge("world_tile_entities",
				"Loaded tile entities: chests, furnaces, hoppers. Hoppers are the first cause of "
						+ "heavy ticks on a build server.",
				"world");
		chunks = r.gauge("world_chunks_loaded", "Loaded chunks.", "world");
		players = r.gauge("world_players", "Players in the world.", "world");
		byType = r.gauge("world_entities_by_type",
				"Entities by type. Collected in the background, and only types above the "
						+ "threshold are published, to keep cardinality bounded.",
				"world", "entity_type");
		worldTime = r.gauge("world_time_ticks", "World time, in ticks.", "world");
		weather = r.gauge("world_weather",
				"Weather. 0 = clear, 1 = rain, 2 = thunder.", "world");
	}

	@Override
	public void collect(MetricRegistry r) {
		// Worlds come and go (Multiverse loads and unloads them). Without a reset, an unloaded
		// world would keep its last value forever.
		byType.clear();

		for (World world : Bukkit.getWorlds()) {
			String name = world.getName();
			entities.set(world.getEntityCount(), name);
			tileEntities.set(world.getTileEntityCount(), name);
			chunks.set(world.getChunkCount(), name);
			players.set(world.getPlayers().size(), name);
			worldTime.set(world.getFullTime(), name);
			weather.set(weatherCode(world), name);

			if (typeThreshold < 0) {
				continue;
			}
			Map<String, Integer> counts = new HashMap<>();
			for (Entity e : world.getEntities()) {
				counts.merge(e.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
			}
			counts.forEach((type, n) -> {
				if (n >= typeThreshold) {
					byType.set(n, name, type);
				}
			});
		}
	}

	private static int weatherCode(World world) {
		if (world.isThundering()) {
			return 2;
		}
		if (world.hasStorm()) {
			return 1;
		}
		return 0;
	}
}
