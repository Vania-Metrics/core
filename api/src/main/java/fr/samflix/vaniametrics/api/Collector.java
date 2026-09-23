package fr.samflix.vaniametrics.api;

/**
 * A source of metrics.
 *
 * <p>There are two modes, and mixing them up creates the very lag the plugin is meant to measure:
 *
 * <ul>
 *   <li><b>on scrape</b> ({@link #isBackground()} false): called by the HTTP thread while
 *       Prometheus waits. Only for things that cost microseconds: a JVM counter, a cgroup file,
 *       {@code World.getEntityCount()}. Fifteen milliseconds here is a lost tick every scrape.
 *   <li><b>background</b> ({@link #isBackground()} true): called by a periodic task that stores
 *       the result in the instruments; the scrape returns the last known value. Use it for
 *       anything that walks a list, queries a database or reads a disk.
 * </ul>
 *
 * <p>A failing collector must not take the scrape down with it. The exporter catches its
 * exceptions, counts them in {@code mc_exporter_scrape_errors_total} and moves on: an unreachable
 * database must not make TPS disappear.
 */
public interface Collector {

	/** A short name, used as a label in the exporter's own metrics and as a config key. */
	String name();

	/**
	 * Where the numbers come from.
	 *
	 * <p>{@code "core"} for what the server or the machine exposes by itself, otherwise the plugin
	 * name: "LuckPerms", "BetonQuest", "spark". Published in {@code mc_exporter_collector_info},
	 * so the origin of a metric can be queried in PromQL instead of looked up in a document that
	 * will eventually be wrong:
	 *
	 * <pre>{@code mc_exporter_collector_info{collector="quest"}  ->  source="BetonQuest"}</pre>
	 */
	default String source() {
		return "core";
	}

	/** Declares the instruments. Called once, when the collector is registered. */
	default void declare(MetricRegistry r) {}

	/** Updates the values. Called on every scrape, or periodically if {@link #isBackground()}. */
	void collect(MetricRegistry r) throws Exception;

	/** See the class docs. False by default: only move to the background once the cost is proven. */
	default boolean isBackground() {
		return false;
	}

	/** Interval in seconds for a background collector. Ignored otherwise. */
	default long intervalSeconds() {
		return 30;
	}

	/**
	 * Whether {@link #collect} must run on the server's main thread.
	 *
	 * <p>True for anything that touches the Bukkit API: worlds, entities, players. False for the
	 * JVM, cgroups, disk and SQL, which would only steal tick time there.
	 */
	default boolean needsMainThread() {
		return false;
	}

	/** Releases resources on unregister: connections, listeners. */
	default void close() {}
}
