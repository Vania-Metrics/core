package fr.samflix.vaniametrics.core.game;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * The game loop (spark has its own collector).
 *
 * <p>TPS is not enough; MSPT is the real metric. TPS is capped at 20: a server taking 45 ms per
 * tick shows 20.0 while it catches up and only drops to 19 once past 50 ms, which is too late.
 * Tick duration rises with load from the first millisecond.
 *
 * <p>Raw tick durations go into a histogram, so Grafana computes any quantile over any window.
 * Each tick is counted once: the loader's buffer is a sliding window that overlaps between
 * scrapes, hence tracking the last tick seen.
 */
public final class TickCollector implements Collector {

	private static final String[] TPS_WINDOWS = {"1m", "5m", "15m"};

	private final GameServer server;

	private Gauge tps;
	private Gauge averageTick;
	private Histogram tickDuration;
	private Counter ticks;
	private Gauge maxPlayers;

	private int lastTickSeen = -1;

	public TickCollector(GameServer server) {
		this.server = server;
	}

	@Override
	public String name() {
		return "tick";
	}

	@Override
	public boolean needsMainThread() {
		return server.needsMainThread();
	}

	@Override
	public void declare(MetricRegistry r) {
		tps = r.gauge("server_tps",
				"Ticks per second, capped at 20. window = 1m|5m|15m. Do NOT alert on it: it only "
						+ "moves once the server is already behind.",
				"window");
		averageTick = r.gauge("server_tick_average_seconds",
				"Average tick duration, as computed by the server.");
		tickDuration = r.histogram("server_tick_duration_seconds",
				"Tick duration distribution. 0.05 s is the threshold: above it the server falls "
						+ "behind. This is THE alerting signal.",
				Histogram.TICK_SECONDS);
		ticks = r.counter("server_ticks_total", "Ticks since startup.");
		maxPlayers = r.gauge("server_players_max", "Player slots advertised by the server.");
	}

	@Override
	public void collect(MetricRegistry r) {
		double[] values = server.tps();
		if (values != null) {
			for (int i = 0; i < values.length && i < TPS_WINDOWS.length; i++) {
				tps.set(Math.min(values[i], 20.0), TPS_WINDOWS[i]);
			}
		}

		double average = server.averageTickMillis();
		if (!Double.isNaN(average)) {
			averageTick.set(average / 1000.0);
		}

		int currentTick = server.currentTick();
		if (currentTick >= 0) {
			ticks.mirror(currentTick);
			long[] durations = server.recentTickDurations();
			if (durations.length > 0) {
				// Most recent last. Only take the ticks since the previous collection, at most the
				// whole buffer.
				int fresh = lastTickSeen < 0 ? durations.length
						: Math.min(currentTick - lastTickSeen, durations.length);
				for (int i = durations.length - fresh; i < durations.length; i++) {
					if (durations[i] > 0) {
						tickDuration.observe(durations[i] / 1e9);
					}
				}
			}
			lastTickSeen = currentTick;
		}

		maxPlayers.set(server.maxPlayers());
	}
}
