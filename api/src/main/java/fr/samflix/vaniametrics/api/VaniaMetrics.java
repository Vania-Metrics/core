package fr.samflix.vaniametrics.api;

/**
 * The contract between the core plugin and collector plugins.
 *
 * <p>The core is one plugin; each integration is another, in its own jar. They only know each
 * other through this interface, which lives in the {@code vania-metrics-api} artifact, the only
 * one a third party needs to compile against.
 *
 * <pre>{@code
 * // on enable
 * VaniaMetrics metrics = VaniaMetricsProvider.get();
 * collector = new MyCollector(metrics.platform());
 * metrics.register(collector);
 *
 * // on disable
 * metrics.unregister(collector);
 * }</pre>
 *
 * <p>Registration order does not matter. A collector registered after the HTTP server started is
 * declared and scheduled immediately and shows up on the next scrape, so a collector plugin can be
 * reloaded without touching the core.
 */
public interface VaniaMetrics {

	/** The registry to declare instruments in. */
	MetricRegistry registry();

	/** The host platform: logging, scheduler, service lookup. */
	Platform platform();

	/**
	 * The core configuration.
	 *
	 * <p>A collector reads its own keys here, by convention {@code module.<name>.<key>}, instead of
	 * opening a file of its own. One file for the operator, overridden by the environment the same
	 * way.
	 */
	Config config();

	/** The core version. */
	String version();

	/**
	 * Puts a collector into service.
	 *
	 * <p>The collector is declared, then called on every scrape or scheduled in the background,
	 * depending on {@link Collector#isBackground()}. Registering the same collector twice does
	 * nothing.
	 */
	void register(Collector collector);

	/**
	 * Removes a collector.
	 *
	 * <p>Call it when the collector plugin is disabled. Otherwise the core keeps calling a
	 * collector whose classes come from an unloaded plugin, and every scrape fails with
	 * {@code NoClassDefFoundError}. Series already published disappear.
	 */
	void unregister(Collector collector);
}
