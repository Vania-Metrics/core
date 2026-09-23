package fr.samflix.vaniametrics.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry: holds the instruments and renders them in the Prometheus text format.
 *
 * <p>No client library on purpose. The exposition format fits on one page; pulling in
 * {@code simpleclient} would add a dependency to relocate in a plugin jar, with the class conflicts
 * that follow when two plugins bundle different versions of it.
 */
public final class MetricRegistry {

	/**
	 * The prefix of every metric.
	 *
	 * <p>One prefix per subject is a Prometheus convention: it makes {@code mc_} usable for
	 * autocompletion in Grafana and keeps server metrics apart from node metrics.
	 */
	public static final String PREFIX = "mc_";

	/**
	 * Allowed domains, and the naming convention.
	 *
	 * <pre>
	 *   mc_&lt;domain&gt;_&lt;subject&gt;[_&lt;unit&gt;]
	 * </pre>
	 *
	 * <p>The domain says where the measurement comes from. {@code mc_server_tps} is provided by the
	 * game server itself; {@code mc_economy_total} comes from a plugin, and which one is answered by
	 * {@code mc_exporter_collector_info}.
	 *
	 * <table border="1">
	 *   <caption>Domains</caption>
	 *   <tr><th>domain</th><th>source</th><th>covers</th></tr>
	 *   <tr><td>{@code server}</td><td>game server</td><td>tps, ticks, players, deaths, blocks, chat</td></tr>
	 *   <tr><td>{@code world}</td><td>game server</td><td>entities, chunks, tile entities, weather</td></tr>
	 *   <tr><td>{@code proxy}</td><td>Velocity</td><td>players, backend servers</td></tr>
	 *   <tr><td>{@code jvm}</td><td>JVM</td><td>memory, garbage collection, threads</td></tr>
	 *   <tr><td>{@code host}</td><td>container</td><td>CPU, memory, disk, I/O</td></tr>
	 *   <tr><td>{@code economy}</td><td>plugin</td><td>currencies, balances, flows</td></tr>
	 *   <tr><td>{@code quest}</td><td>plugin</td><td>tags, points, journal</td></tr>
	 *   <tr><td>{@code permission}</td><td>plugin</td><td>groups, tracks</td></tr>
	 *   <tr><td>{@code network}</td><td>plugin</td><td>packets, client versions</td></tr>
	 *   <tr><td>{@code multiverse}</td><td>plugin</td><td>declared worlds, loaded or not</td></tr>
	 *   <tr><td>{@code spark}</td><td>plugin</td><td>tick quantiles, CPU, allocation</td></tr>
	 *   <tr><td>{@code anticheat}</td><td>plugin</td><td>violations, triggered checks</td></tr>
	 *   <tr><td>{@code mob}</td><td>plugin</td><td>custom mobs spawned, killed</td></tr>
	 *   <tr><td>{@code region}</td><td>plugin</td><td>protected regions, denied actions</td></tr>
	 *   <tr><td>{@code pregen}</td><td>plugin</td><td>pregeneration progress</td></tr>
	 *   <tr><td>{@code inventory}</td><td>plugin</td><td>per-world inventory switches</td></tr>
	 *   <tr><td>{@code portal}</td><td>plugin</td><td>portal uses</td></tr>
	 *   <tr><td>{@code nova}</td><td>plugin</td><td>custom blocks and items</td></tr>
	 *   <tr><td>{@code crate}</td><td>plugin</td><td>crates opened, rewards drawn, keys</td></tr>
	 *   <tr><td>{@code placeholder}</td><td>plugin</td><td>values read through PlaceholderAPI</td></tr>
	 *   <tr><td>{@code exporter}</td><td>this plugin</td><td>its own health</td></tr>
	 *   <tr><td>{@code build}</td><td>this plugin</td><td>version information</td></tr>
	 * </table>
	 *
	 * <p>Per-player detail stays in its domain as a sub-segment: a player's balance is
	 * {@code mc_economy_player_balance}, not {@code mc_player_balance}. Domain first, always;
	 * otherwise {@code mc_player_*} becomes a catch-all where nothing says where anything comes
	 * from.
	 *
	 * <p>The list is closed and enforced at declaration. A convention that only lives in a
	 * document drifts by the third collector; this one refuses to load. Adding a domain is a
	 * deliberate change to this set.
	 */
	private static final Set<String> DOMAINS = Set.of(
			// Exposed by the server and the machine themselves.
			"server", "world", "proxy", "jvm", "host",
			// Bedrock players, as seen by Geyser.
			"bedrock",
			// Backed by a plugin.
			"economy", "quest", "permission", "network", "multiverse", "spark",
			"anticheat", "mob", "region", "pregen", "inventory", "portal", "nova",
			"placeholder", "crate",
			// The exporter itself.
			"exporter", "build");

	private final Map<String, Metric> instruments = new ConcurrentHashMap<>();

	/** Declares a gauge. Calling it again with the same name returns the same instrument. */
	public Gauge gauge(String name, String help, String... labelNames) {
		checkDomain(name);
		return (Gauge) instruments.computeIfAbsent(
				PREFIX + name, n -> new Gauge(n, help, labelNames));
	}

	/** Declares a counter. The name must end in {@code _total}. */
	public Counter counter(String name, String help, String... labelNames) {
		checkDomain(name);
		if (!name.endsWith("_total")) {
			throw new IllegalArgumentException("a counter name must end in _total: " + name);
		}
		return (Counter) instruments.computeIfAbsent(
				PREFIX + name, n -> new Counter(n, help, labelNames));
	}

	/** Declares a histogram. The name carries the unit, no {@code _total} suffix. */
	public Histogram histogram(String name, String help, double[] buckets, String... labelNames) {
		checkDomain(name);
		return (Histogram) instruments.computeIfAbsent(
				PREFIX + name, n -> new Histogram(n, help, buckets, labelNames));
	}

	/**
	 * Enforces the naming convention at declaration.
	 *
	 * <p>Failing here is the point: a misnamed collector does not load, the message says what to
	 * fix, and the others keep running because the exporter catches the exception.
	 */
	private static void checkDomain(String name) {
		int sep = name.indexOf('_');
		// A domain alone ("server") names nothing: the subject is part of the convention too.
		String domain = sep < 0 || sep == name.length() - 1 ? "" : name.substring(0, sep);
		if (!DOMAINS.contains(domain)) {
			throw new IllegalArgumentException(
					"'" + name + "' has no known domain. Expected mc_<domain>_<subject>, "
							+ "domain one of " + new TreeSet<>(DOMAINS)
							+ " (see MetricRegistry).");
		}
	}

	Collection<Metric> instruments() {
		return instruments.values();
	}

	/**
	 * Renders the whole registry in the Prometheus text format, version 0.0.4.
	 *
	 * <p>Families are sorted by name. Prometheus does not require it, but a {@code curl /metrics}
	 * that reads well by eye is the best debugging tool when something is off.
	 */
	public String render() {
		StringBuilder out = new StringBuilder(16 * 1024);
		instruments.values().stream()
				.sorted(Comparator.comparing(m -> m.name))
				.forEach(m -> renderMetric(out, m));
		return out.toString();
	}

	private void renderMetric(StringBuilder out, Metric m) {
		if (m.series.isEmpty()) {
			return;
		}
		out.append("# HELP ").append(m.name).append(' ').append(escapeHelp(m.help)).append('\n');
		out.append("# TYPE ").append(m.name).append(' ').append(m.type()).append('\n');

		for (Map.Entry<Metric.LabelValues, double[]> e : m.series.entrySet()) {
			String[] values = e.getKey().values;
			double[] s = e.getValue();
			if (m instanceof Histogram h) {
				renderHistogram(out, h, values, s);
			} else {
				line(out, m.name, m.labelNames, values, null, null, s[0]);
			}
		}
	}

	private void renderHistogram(StringBuilder out, Histogram h, String[] values, double[] s) {
		// Buckets are cumulative and must come out in increasing order: Prometheus relies on it
		// to interpolate quantiles.
		for (int i = 0; i < h.bounds.length; i++) {
			line(out, h.name + "_bucket", h.labelNames, values, "le", number(h.bounds[i]), s[i]);
		}
		double count = s[h.bounds.length + 1];
		line(out, h.name + "_bucket", h.labelNames, values, "le", "+Inf", count);
		line(out, h.name + "_sum", h.labelNames, values, null, null, s[h.bounds.length]);
		line(out, h.name + "_count", h.labelNames, values, null, null, count);
	}

	private void line(StringBuilder out, String name, String[] labelNames, String[] labelValues,
			String extraName, String extraValue, double value) {
		out.append(name);
		if (labelNames.length > 0 || extraName != null) {
			out.append('{');
			for (int i = 0; i < labelNames.length; i++) {
				if (i > 0) {
					out.append(',');
				}
				out.append(labelNames[i]).append("=\"").append(escape(labelValues[i])).append('"');
			}
			if (extraName != null) {
				if (labelNames.length > 0) {
					out.append(',');
				}
				out.append(extraName).append("=\"").append(extraValue).append('"');
			}
			out.append('}');
		}
		out.append(' ').append(number(value)).append('\n');
	}

	/**
	 * Formats a sample value.
	 *
	 * <p>{@code Locale.ROOT} is mandatory: under a French or German locale, {@code %f} writes
	 * "20,0" and Prometheus rejects the whole line.
	 *
	 * <p>Integers are written without decimals to keep the output readable, and special values use
	 * Prometheus' spelling, which differs from Java's.
	 */
	static String number(double v) {
		if (Double.isNaN(v)) {
			return "NaN";
		}
		if (v == Double.POSITIVE_INFINITY) {
			return "+Inf";
		}
		if (v == Double.NEGATIVE_INFINITY) {
			return "-Inf";
		}
		if (v == Math.rint(v) && Math.abs(v) < 1e15) {
			return String.format(Locale.ROOT, "%d", (long) v);
		}
		double abs = Math.abs(v);
		if (abs < 1e-6 || abs >= 1e15) {
			// Scientific notation: Prometheus accepts it, and 1e-9 written out in decimal is
			// unreadable for a value that means nothing at that scale anyway.
			return Double.toString(v);
		}
		// The shortest representation that parses back to the same double. "%.6g" printed 0.001
		// as "0.00100000": correct, but bucket bounds are read by eye.
		return new BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString();
	}

	/** Label values need three characters escaped. */
	static String escape(String v) {
		if (v == null) {
			return "";
		}
		return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	/** Help text needs only two; quotes are allowed there. */
	static String escapeHelp(String v) {
		return v.replace("\\", "\\\\").replace("\n", "\\n");
	}
}
