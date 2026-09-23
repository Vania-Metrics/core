package fr.samflix.vaniametrics.api;

/**
 * A value that goes up and down: online players, used memory, TPS.
 *
 * <p>The default instrument. The deciding question is "can the value go down?" If it only grows
 * until a restart, it is a {@link Counter}, and the difference matters: Grafana applies
 * {@code rate()} to counters, never to gauges.
 */
public final class Gauge extends Metric {

	Gauge(String name, String help, String... labelNames) {
		super(name, help, labelNames);
	}

	@Override
	String type() {
		return "gauge";
	}

	/** Sets the value of the series identified by these label values. */
	public void set(double value, String... labels) {
		seriesFor(1, labels)[0] = value;
	}

	/** Adds to the series. Useful to aggregate world by world. */
	public void add(double delta, String... labels) {
		double[] s = seriesFor(1, labels);
		synchronized (s) {
			s[0] += delta;
		}
	}
}
