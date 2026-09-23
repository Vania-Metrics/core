package fr.samflix.vaniametrics.core.game;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.PlayerRef;
import fr.samflix.vaniametrics.api.PlayerSeries;
import fr.samflix.vaniametrics.core.game.PlayerSnapshot.Stat;

/**
 * Online players, at two levels.
 *
 * <p><b>Aggregates</b>: sums, histograms, breakdowns over bounded sets such as client brand or
 * locale. No player label; they answer "how many, when".
 *
 * <p><b>Per-player detail</b>, only for online players and under a cap; see {@link PlayerSeries}.
 * It answers "who", which no aggregate can, without a database or a log pipeline.
 *
 * <p>Vanilla statistics are per player and cumulative forever; they are summed over online
 * players. That total drops when a player leaves, so it is a gauge, not a counter.
 */
public final class PlayerCollector implements Collector {

	private final GameServer server;
	private final Config config;

	private Gauge online;
	private Histogram ping;
	private Gauge byBrand;
	private Gauge byLocale;
	private Gauge statistics;
	private Counter playtime;

	// Handed to PlayerSeries, which clears them when the set of online players changes.
	private Gauge playerPing;
	private Gauge playerPlaytime;
	private Gauge playerStatistic;
	private PlayerSeries series;

	public PlayerCollector(GameServer server, Config config) {
		this.server = server;
		this.config = config;
	}

	@Override
	public String name() {
		return "players";
	}

	@Override
	public boolean needsMainThread() {
		return server.needsMainThread();
	}

	@Override
	public void declare(MetricRegistry r) {
		online = r.gauge("server_players_online", "Online players.");
		ping = r.histogram("server_players_ping_seconds",
				"Ping distribution of online players.", Histogram.PING_SECONDS);
		// No client version here: loaders do not expose it. It comes from ViaVersion or
		// PacketEvents, so from a collector plugin.
		byBrand = r.gauge("server_players_by_brand",
				"Players by advertised client brand. A mod can fake it: it describes honest "
						+ "players, not cheaters.",
				"brand");
		byLocale = r.gauge("server_players_by_locale", "Players by client locale.", "locale");
		statistics = r.gauge("server_player_statistics",
				"Sum of a vanilla statistic over ONLINE players. Drops when they leave: a gauge, "
						+ "not a counter.",
				"statistic");
		playtime = r.counter("server_playtime_seconds_total",
				"Cumulative playtime of all online players.");

		playerPing = r.gauge("server_player_ping_seconds",
				"Ping of an online player.", "player", "uuid");
		playerPlaytime = r.gauge("server_player_playtime_seconds",
				"Total playtime of an online player.", "player", "uuid");
		playerStatistic = r.gauge("server_player_statistic",
				"A vanilla statistic, per player. Published while the player is online, which is "
						+ "enough since nothing happens offline.",
				"player", "uuid", "statistic");
		series = new PlayerSeries(r, config);
	}

	@Override
	public void collect(MetricRegistry r) {
		List<PlayerSnapshot> players = server.players();
		online.set(players.size());

		var refs = players.stream().map(PlayerCollector::ref).toList();
		var selected = series.select(refs, playerPing, playerPlaytime, playerStatistic);
		var published = selected.stream().map(PlayerRef::uuid).collect(Collectors.toSet());

		// Reset breakdowns so a brand or locale nobody uses any more disappears instead of
		// staying frozen at its last value.
		byBrand.clear();
		byLocale.clear();

		Map<String, Integer> brands = new HashMap<>();
		Map<String, Integer> locales = new HashMap<>();
		Map<Stat, Long> totals = new HashMap<>();

		for (PlayerSnapshot p : players) {
			ping.observe(p.pingMillis() / 1000.0);
			if (published.contains(p.uuid().toString())) {
				publishDetail(p);
			}
			brands.merge(normalize(p.brand()), 1, Integer::sum);
			locales.merge(normalize(p.locale()), 1, Integer::sum);
			for (Stat s : Stat.values()) {
				totals.merge(s, p.stat(s), Long::sum);
			}
		}

		brands.forEach((b, n) -> byBrand.set(n, b));
		locales.forEach((l, n) -> byLocale.set(n, l));

		statistics.set(totals.getOrDefault(Stat.PLAYER_KILLS, 0L), "player_kills");
		statistics.set(totals.getOrDefault(Stat.MOB_KILLS, 0L), "mob_kills");
		statistics.set(totals.getOrDefault(Stat.DEATHS, 0L), "deaths");
		// Vanilla damage statistics are in tenths of a health point; publish health points.
		statistics.set(totals.getOrDefault(Stat.DAMAGE_DEALT, 0L) / 10.0, "damage_dealt");
		statistics.set(totals.getOrDefault(Stat.DAMAGE_TAKEN, 0L) / 10.0, "damage_taken");
		statistics.set(totals.getOrDefault(Stat.JUMPS, 0L), "jumps");
		playtime.mirror(totals.getOrDefault(Stat.PLAY_TICKS, 0L) / 20.0);
	}

	/** Only called for players the cap lets through. */
	private void publishDetail(PlayerSnapshot p) {
		PlayerRef ref = ref(p);
		playerPing.set(p.pingMillis() / 1000.0, ref.labels());
		playerPlaytime.set(p.stat(Stat.PLAY_TICKS) / 20.0, ref.labels());
		playerStatistic.set(p.stat(Stat.PLAYER_KILLS), ref.labels("player_kills"));
		playerStatistic.set(p.stat(Stat.MOB_KILLS), ref.labels("mob_kills"));
		playerStatistic.set(p.stat(Stat.DEATHS), ref.labels("deaths"));
		playerStatistic.set(p.stat(Stat.DAMAGE_DEALT) / 10.0, ref.labels("damage_dealt"));
		playerStatistic.set(p.stat(Stat.DAMAGE_TAKEN) / 10.0, ref.labels("damage_taken"));
	}

	private static PlayerRef ref(PlayerSnapshot p) {
		return PlayerRef.of(p.uuid(), p.name());
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
	}
}
