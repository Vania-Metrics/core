package fr.samflix.vaniametrics.core.game;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Worlds: entities, tile entities, chunks.
 *
 * <p>The per-type breakdown is O(n), since every entity has to be looked at. So it runs in the
 * background, and it is bounded: only types above a threshold are published. Otherwise a hundred
 * entity types across three worlds would make three hundred time series counting zeros.
 */
public final class WorldCollector implements Collector {

	private final GameServer server;
	private final int typeThreshold;

	private Gauge entities;
	private Gauge tileEntities;
	private Gauge chunks;
	private Gauge players;
	private Gauge byType;
	private Gauge worldTime;
	private Gauge weather;

	public WorldCollector(GameServer server, Config config) {
		this.server = server;
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
		// Because of the per-type breakdown alone. Ten seconds of delay on an entity count has
		// never changed a diagnosis.
		return true;
	}

	@Override
	public long intervalSeconds() {
		return 15;
	}

	@Override
	public void declare(MetricRegistry r) {
		entities = r.gauge("world_entities", "Loaded entities.", "world");
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
		entities.clear();
		tileEntities.clear();
		chunks.clear();
		players.clear();
		byType.clear();
		worldTime.clear();
		weather.clear();

		for (WorldSnapshot w : server.worlds(typeThreshold >= 0)) {
			String name = w.name();
			entities.set(w.entities(), name);
			if (w.tileEntities() >= 0) {
				tileEntities.set(w.tileEntities(), name);
			}
			chunks.set(w.chunks(), name);
			players.set(w.players(), name);
			worldTime.set(w.fullTime(), name);
			weather.set(w.weather(), name);
			w.entitiesByType().forEach((type, n) -> {
				if (n >= typeThreshold) {
					byType.set(n, name, type);
				}
			});
		}
	}
}
