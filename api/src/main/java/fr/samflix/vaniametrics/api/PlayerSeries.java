package fr.samflix.vaniametrics.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The only place where labelling by player is allowed.
 *
 * <p>A {@code player="..."} label creates one time series per player. "Never do it" is the right
 * rule at a thousand players and the wrong one for a small server: fifty slots and seven player
 * heads make 350 series, a few megabytes, next to the ~300 series the server already publishes.
 * What matters is the bound, and this class is the bound.
 *
 * <p>Two guarantees:
 *
 * <ol>
 *   <li><b>Only online players are published.</b> Nothing is lost: a player can only trigger an
 *       event while online (find a head, earn money, die), so a series that lives for the session
 *       captures everything they do. When they leave, Prometheus writes a staleness marker and the
 *       series ends cleanly, its history kept.
 *   <li><b>A hard cap.</b> Beyond it, per-player detail stops and
 *       {@code mc_exporter_player_series_dropped} says so. Announced degradation beats a
 *       Prometheus that grows silently.
 * </ol>
 *
 * <p>Still forbidden, without exception: per-player labels on high-frequency metrics such as a
 * packet or a broken block. This publishes states, not flows.
 *
 * <p>Two labels per player, {@code player} and {@code uuid}; see {@link PlayerRef}.
 */
public final class PlayerSeries {

	private final Config config;
	private final Gauge dropped;

	/**
	 * Players published on the previous pass, to know which series to drop.
	 *
	 * <p>Keyed by UUID, not name, so a player renaming mid-session is not counted as two people.
	 */
	private final Set<String> published = new LinkedHashSet<>();

	public PlayerSeries(MetricRegistry registry, Config config) {
		this.config = config;
		this.dropped = registry.gauge("exporter_player_series_dropped",
				"Players whose detail is NOT published because of the "
						+ "collector.players.max_series cap. A non-zero value means per-player "
						+ "metrics are incomplete.");
	}

	/** Whether per-player detail is enabled ({@code collector.players.per_player}). */
	public boolean isEnabled() {
		return config.getBoolean("collector.players.per_player", true);
	}

	/**
	 * Decides who to publish and clears the series of players who no longer are.
	 *
	 * <p>Call it at the start of each collection with the online players. The given instruments are
	 * cleared when the set of players changes; otherwise a player who left would keep their last
	 * value forever.
	 *
	 * @return the players to publish, cap applied. Empty if per-player detail is disabled.
	 */
	public Collection<PlayerRef> select(Collection<PlayerRef> online, Metric... instruments) {
		if (!isEnabled()) {
			clear(instruments);
			dropped.set(online.size());
			return List.of();
		}
		int cap = config.getInt("collector.players.max_series", 200);
		List<PlayerRef> selected = new ArrayList<>();
		Set<String> uuids = new LinkedHashSet<>();
		int skipped = 0;
		for (PlayerRef p : online) {
			if (selected.size() < cap) {
				selected.add(p);
				uuids.add(p.uuid());
			} else {
				skipped++;
			}
		}
		dropped.set(skipped);

		// Clear everything and republish: removing series one by one would require knowing each
		// instrument's labels, which only the caller does. Clearing costs an emptied map,
		// republishing costs what was going to be written anyway.
		if (!published.equals(uuids)) {
			clear(instruments);
			published.clear();
			published.addAll(uuids);
		}
		return selected;
	}

	private void clear(Metric... instruments) {
		for (Metric m : instruments) {
			if (m != null) {
				m.clear();
			}
		}
	}
}
