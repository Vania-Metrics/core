package fr.samflix.vaniametrics.sponge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.spongepowered.api.Server;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.network.ServerConnectionState;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.statistic.Statistic;
import org.spongepowered.api.statistic.Statistics;
import org.spongepowered.api.world.chunk.WorldChunk;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.api.world.weather.WeatherType;
import org.spongepowered.api.world.weather.WeatherTypes;

import fr.samflix.vaniametrics.core.game.GameServer;
import fr.samflix.vaniametrics.core.game.PlayerSnapshot;
import fr.samflix.vaniametrics.core.game.PlayerSnapshot.Stat;
import fr.samflix.vaniametrics.core.game.TpsMeter;
import fr.samflix.vaniametrics.core.game.WorldSnapshot;

/** {@link GameServer} over SpongeAPI. Called on the main thread. */
final class SpongeGameServer implements GameServer {

	private static final long[] NO_DURATIONS = new long[0];

	private final Server server;
	private final TpsMeter tpsMeter;

	SpongeGameServer(Server server, TpsMeter tpsMeter) {
		this.server = server;
		this.tpsMeter = tpsMeter;
	}

	@Override
	public int maxPlayers() {
		return server.maxPlayers();
	}

	@Override
	public double[] tps() {
		return tpsMeter.tps();
	}

	@Override
	public double averageTickMillis() {
		return server.averageTickTime();
	}

	@Override
	public int currentTick() {
		return tpsMeter.ticks();
	}

	@Override
	public long[] recentTickDurations() {
		// SpongeAPI exposes no per-tick durations.
		return NO_DURATIONS;
	}

	@Override
	public List<WorldSnapshot> worlds(boolean withEntityTypes) {
		List<WorldSnapshot> result = new ArrayList<>();
		for (ServerWorld world : server.worldManager().worlds()) {
			var entities = world.entities();
			int chunks = 0;
			for (WorldChunk ignored : world.loadedChunks()) {
				chunks++;
			}
			Map<String, Integer> byType = Map.of();
			if (withEntityTypes) {
				byType = new HashMap<>();
				for (Entity e : entities) {
					byType.merge(e.type().key(RegistryTypes.ENTITY_TYPE).value(), 1, Integer::sum);
				}
			}
			result.add(new WorldSnapshot(world.key().asString(), entities.size(),
					world.blockEntities().size(), chunks, world.players().size(),
					world.properties().gameTime().asTicks().ticks(), weather(world), byType));
		}
		return result;
	}

	@Override
	public List<PlayerSnapshot> players() {
		List<PlayerSnapshot> result = new ArrayList<>();
		for (ServerPlayer p : server.onlinePlayers()) {
			Map<Statistic, Long> raw = p.get(Keys.STATISTICS).orElse(Map.of());
			Map<Stat, Long> stats = new EnumMap<>(Stat.class);
			stats.put(Stat.PLAYER_KILLS, raw.getOrDefault(Statistics.PLAYER_KILLS.get(), 0L));
			stats.put(Stat.MOB_KILLS, raw.getOrDefault(Statistics.MOB_KILLS.get(), 0L));
			stats.put(Stat.DEATHS, raw.getOrDefault(Statistics.DEATHS.get(), 0L));
			stats.put(Stat.DAMAGE_DEALT, raw.getOrDefault(Statistics.DAMAGE_DEALT.get(), 0L));
			stats.put(Stat.DAMAGE_TAKEN, raw.getOrDefault(Statistics.DAMAGE_TAKEN.get(), 0L));
			stats.put(Stat.JUMPS, raw.getOrDefault(Statistics.JUMP.get(), 0L));
			stats.put(Stat.PLAY_TICKS, raw.getOrDefault(Statistics.PLAY_TIME.get(), 0L));
			Locale locale = p.locale();
			// SpongeAPI does not expose the client brand.
			result.add(new PlayerSnapshot(p.uniqueId(), p.name(), latency(p), null,
					locale == null ? null : locale.toLanguageTag(), stats));
		}
		return result;
	}

	private static int latency(ServerPlayer p) {
		return p.connection().state()
				.filter(ServerConnectionState.Game.class::isInstance)
				.map(s -> ((ServerConnectionState.Game) s).latency())
				.orElse(0);
	}

	private static int weather(ServerWorld world) {
		WeatherType type = world.weather().type();
		if (type.equals(WeatherTypes.THUNDER.get())) {
			return 2;
		}
		return type.equals(WeatherTypes.RAIN.get()) ? 1 : 0;
	}
}
