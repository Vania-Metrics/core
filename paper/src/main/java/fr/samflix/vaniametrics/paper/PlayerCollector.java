package fr.samflix.vaniametrics.paper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.PlayerRef;
import fr.samflix.vaniametrics.api.PlayerSeries;

/**
 * Online players, at two levels.
 *
 * <p><b>Aggregates</b>: sums, histograms, breakdowns over bounded sets such as client brand or
 * locale. No player label; they answer "how many, when".
 *
 * <p><b>Per-player detail</b>, only for online players and under a cap; see {@link PlayerSeries}
 * for the reasoning. It answers "who", which no aggregate can, without a database or a log
 * pipeline.
 *
 * <p>Vanilla statistics are per player and cumulative forever; they are summed over online
 * players. That total drops when a player leaves, so it is a gauge, not a counter. Event counters
 * live in {@link GameEventListener}.
 */
final class PlayerCollector implements Collector {

	private final Config config;

	private Gauge online;
	private Histogram ping;
	private Gauge byBrand;
	private Gauge byLocale;
	private Gauge statistics;
	private Counter playtime;

	// Per-player detail. These instruments are handed to PlayerSeries, which clears them when the
	// set of online players changes.
	private Gauge playerPing;
	private Gauge playerPlaytime;
	private Gauge playerStatistic;
	private PlayerSeries series;

	PlayerCollector(Config config) {
		this.config = config;
	}

	@Override
	public String name() {
		return "players";
	}

	@Override
	public boolean needsMainThread() {
		return true;
	}

	@Override
	public void declare(MetricRegistry r) {
		online = r.gauge("server_players_online", "Online players.");
		ping = r.histogram("server_players_ping_seconds",
				"Ping distribution of online players.", Histogram.PING_SECONDS);
		// No client version here: org.bukkit.entity.Player has no method for it. It comes from
		// ViaVersion, so from a collector plugin. The core only reports what the platform exposes.
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
		var players = Bukkit.getOnlinePlayers();
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
		long playerKills = 0;
		long mobKills = 0;
		long deaths = 0;
		long damageDealt = 0;
		long damageTaken = 0;
		long jumps = 0;
		long playTicks = 0;

		for (Player p : players) {
			ping.observe(p.getPing() / 1000.0);
			if (published.contains(p.getUniqueId().toString())) {
				publishDetail(p);
			}
			brands.merge(brand(p), 1, Integer::sum);
			locales.merge(locale(p), 1, Integer::sum);

			playerKills += stat(p, Statistic.PLAYER_KILLS);
			mobKills += stat(p, Statistic.MOB_KILLS);
			deaths += stat(p, Statistic.DEATHS);
			damageDealt += stat(p, Statistic.DAMAGE_DEALT);
			damageTaken += stat(p, Statistic.DAMAGE_TAKEN);
			jumps += stat(p, Statistic.JUMP);
			playTicks += stat(p, Statistic.PLAY_ONE_MINUTE);
		}

		brands.forEach((b, n) -> byBrand.set(n, b));
		locales.forEach((l, n) -> byLocale.set(n, l));

		statistics.set(playerKills, "player_kills");
		statistics.set(mobKills, "mob_kills");
		statistics.set(deaths, "deaths");
		// Vanilla damage statistics are in tenths of a health point; publish health points.
		statistics.set(damageDealt / 10.0, "damage_dealt");
		statistics.set(damageTaken / 10.0, "damage_taken");
		statistics.set(jumps, "jumps");
		// PLAY_ONE_MINUTE counts ticks despite its name, a classic API trap.
		playtime.mirror(playTicks / 20.0);
	}

	/** Only called for players the cap lets through. */
	private void publishDetail(Player p) {
		PlayerRef ref = ref(p);
		playerPing.set(p.getPing() / 1000.0, ref.labels());
		playerPlaytime.set(stat(p, Statistic.PLAY_ONE_MINUTE) / 20.0, ref.labels());
		playerStatistic.set(stat(p, Statistic.PLAYER_KILLS), ref.labels("player_kills"));
		playerStatistic.set(stat(p, Statistic.MOB_KILLS), ref.labels("mob_kills"));
		playerStatistic.set(stat(p, Statistic.DEATHS), ref.labels("deaths"));
		playerStatistic.set(stat(p, Statistic.DAMAGE_DEALT) / 10.0, ref.labels("damage_dealt"));
		playerStatistic.set(stat(p, Statistic.DAMAGE_TAKEN) / 10.0, ref.labels("damage_taken"));
	}

	private static PlayerRef ref(Player p) {
		return PlayerRef.of(p.getUniqueId(), p.getName());
	}

	private int stat(Player p, Statistic s) {
		try {
			return p.getStatistic(s);
		} catch (IllegalArgumentException e) {
			// A statistic removed by a Minecraft version. Zero rather than a failure.
			return 0;
		}
	}

	private String brand(Player p) {
		String b = p.getClientBrandName();
		return b == null || b.isBlank() ? "unknown" : b.toLowerCase(Locale.ROOT);
	}

	private String locale(Player p) {
		// locale() rather than the deprecated getLocale(): it returns an already normalised
		// java.util.Locale instead of the client's raw string.
		Locale l = p.locale();
		return l == null ? "unknown" : l.toLanguageTag().toLowerCase(Locale.ROOT);
	}
}
