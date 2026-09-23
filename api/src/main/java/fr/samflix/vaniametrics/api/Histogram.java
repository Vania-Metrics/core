package fr.samflix.vaniametrics.api;

/**
 * A distribution: tick durations, player ping, session length.
 *
 * <p>Not an average, because an average hides exactly what matters. A server where 99% of ticks
 * take 1 ms and 1% take 400 ms averages 5 ms: a reassuring number for a server that stutters four
 * times a second. A histogram keeps the shape of the distribution and lets Grafana compute any
 * quantile afterwards, over any window, with {@code histogram_quantile()}.
 *
 * <p>What Prometheus requires:
 * <ul>
 *   <li><b>cumulative</b> buckets: {@code le="0.05"} counts everything ≤ 50 ms, including what is
 *       already in {@code le="0.01"};
 *   <li>a final {@code le="+Inf"} bucket equal to the total count;
 *   <li>{@code _sum} and {@code _count} series alongside.
 * </ul>
 *
 * <p>The cost is in series: a 12-bucket histogram publishes 14 time series per label combination.
 * That is why no histogram in this plugin is labelled by player.
 */
public final class Histogram extends Metric {

	/**
	 * Seconds, from 1 ms to 2 s, sized for a game loop running at 50 ms per tick.
	 *
	 * <p>0.05 is a full tick and the threshold between "the server keeps up" and "the server falls
	 * behind". The buckets above it tell by how much.
	 */
	public static final double[] TICK_SECONDS = {
		0.001, 0.005, 0.010, 0.025, 0.050, 0.075, 0.100, 0.250, 0.500, 1.0, 2.0
	};

	/** Seconds, from 5 ms to 2 s: player ping, from LAN to the other side of the world. */
	public static final double[] PING_SECONDS = {
		0.005, 0.010, 0.025, 0.050, 0.100, 0.150, 0.200, 0.300, 0.500, 1.0, 2.0
	};

	/** Seconds, from one minute to six hours: the length of a play session. */
	public static final double[] SESSION_SECONDS = {
		60, 300, 900, 1800, 3600, 7200, 14400, 21600
	};

	final double[] bounds;

	Histogram(String name, String help, double[] bounds, String... labelNames) {
		super(name, help, labelNames);
		this.bounds = bounds;
	}

	@Override
	String type() {
		return "histogram";
	}

	/**
	 * Records an observation.
	 *
	 * <p>The internal array has {@code bounds.length + 2} slots: one count per bound, then the sum,
	 * then the total count. The {@code +Inf} bucket is not stored; it equals the count.
	 */
	public void observe(double value, String... labels) {
		double[] s = seriesFor(bounds.length + 2, labels);
		synchronized (s) {
			for (int i = 0; i < bounds.length; i++) {
				if (value <= bounds[i]) {
					s[i]++;
				}
			}
			s[bounds.length] += value;
			s[bounds.length + 1]++;
		}
	}
}
