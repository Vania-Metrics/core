package fr.samflix.vaniametrics.api;

/**
 * A value that only goes up: deaths, logins, bytes received.
 *
 * <p>It never goes down; that is the contract. Prometheus treats any decrease as a process restart
 * and adjusts its rate calculation, so a reset "to keep things tidy" produces a spike that never
 * happened. The name ends in {@code _total}, which Grafana and alerting rules expect.
 *
 * <p>Dashboards almost never show the raw value but {@code rate(x[5m])}, "how many per second".
 * That is why a counter beats a hand-maintained gauge: the rate stays correct even when a scrape
 * is missed.
 */
public final class Counter extends Metric {

	Counter(String name, String help, String... labelNames) {
		super(name, help, labelNames);
	}

	@Override
	String type() {
		return "counter";
	}

	public void inc(String... labels) {
		add(1, labels);
	}

	/**
	 * Copies a cumulative value maintained elsewhere.
	 *
	 * <p>For counters that something else already keeps since startup: the kernel's
	 * {@code cpu.stat}, the JVM's {@code getCollectionCount()}, {@code Statistic.PLAYER_KILLS}.
	 * Setting the absolute value instead of computing a delta keeps no state and stays correct
	 * when a collection is skipped.
	 *
	 * <p>Do not replace this with {@code clear()} followed by {@code add()}: on a labelled counter,
	 * {@code clear()} would wipe the series of every other label value.
	 */
	public void mirror(double value, String... labels) {
		if (Double.isNaN(value)) {
			return;
		}
		double[] s = seriesFor(1, labels);
		synchronized (s) {
			s[0] = value;
		}
	}

	/** Adds {@code delta}. A negative delta is ignored rather than breaking the contract. */
	public void add(double delta, String... labels) {
		if (delta < 0) {
			return;
		}
		double[] s = seriesFor(1, labels);
		synchronized (s) {
			s[0] += delta;
		}
	}
}
