package fr.samflix.vaniametrics.api;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common base of the three instruments.
 *
 * <p>One family, many series. An instrument has a name, a help text and a map of series keyed by
 * label values. {@code mc_world_entities} is a family; {@code mc_world_entities{world="lobby"}} is
 * one of its series.
 *
 * <p>Instruments are written from the server's event threads (main thread, PacketEvents' netty
 * threads, async tasks) and read by the HTTP thread on scrape, hence the concurrent map and the
 * per-series locking. Without them the scrape would read half-written values.
 */
public abstract class Metric {

	/** Full name, prefix included: {@code mc_server_tps}. */
	public final String name;
	/** The {@code # HELP} line. */
	public final String help;
	/** Label names, in order. Values are supplied on write. */
	public final String[] labelNames;

	final Map<LabelValues, double[]> series = new ConcurrentHashMap<>();

	Metric(String name, String help, String... labelNames) {
		this.name = name;
		this.help = help;
		this.labelNames = labelNames;
	}

	/** The word on the {@code # TYPE} line. */
	abstract String type();

	/**
	 * The series for these label values, created if needed.
	 *
	 * <p>The returned array is the series' internal state. Its length depends on the instrument:
	 * 1 for a gauge or a counter, {@code buckets + 2} for a histogram.
	 */
	double[] seriesFor(int size, String... values) {
		if (values.length != labelNames.length) {
			throw new IllegalArgumentException(
					name + " expects " + labelNames.length + " label(s), got " + values.length);
		}
		return series.computeIfAbsent(new LabelValues(values), k -> new double[size]);
	}

	/**
	 * Drops every series.
	 *
	 * <p>Required for gauges whose label values come and go: an unloaded world, an entity type that
	 * disappeared, a client version nobody uses any more. Without a reset their last value would be
	 * published forever. Never clear a counter: Prometheus would read it as a restart.
	 */
	public void clear() {
		series.clear();
	}

	/** A series key: the label values, comparable and hashable. */
	static final class LabelValues {
		final String[] values;
		private final int hash;

		LabelValues(String[] values) {
			this.values = values;
			this.hash = Arrays.hashCode(values);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof LabelValues other && Arrays.equals(values, other.values);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}
}
