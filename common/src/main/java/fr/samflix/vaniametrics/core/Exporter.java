package fr.samflix.vaniametrics.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Platform;
import fr.samflix.vaniametrics.api.Version;
import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;
import fr.samflix.vaniametrics.core.collector.CgroupCollector;
import fr.samflix.vaniametrics.core.collector.DiskCollector;
import fr.samflix.vaniametrics.core.collector.JvmCollector;

/**
 * The orchestrator, and the implementation of the public API.
 *
 * <p>Holds the registry, the collectors and the HTTP server. Platform adapters start it; collector
 * plugins, which ship in their own jars, register through {@link VaniaMetrics}.
 *
 * <p>Registration is dynamic: a collector registered after the core started is declared and
 * scheduled right away, and one whose plugin is unloaded is removed cleanly. Otherwise the scrape
 * would keep calling code whose classloader is gone.
 */
public final class Exporter implements VaniaMetrics {

	private final Platform platform;
	private final Config config;
	private final MetricRegistry registry = new MetricRegistry();

	/** Copy-on-write: read by the HTTP thread on every scrape, written when a collector registers. */
	private final List<Collector> onScrape = new CopyOnWriteArrayList<>();

	private final Map<String, AtomicLong> lastCollection = new ConcurrentHashMap<>();
	private final Map<Collector, AtomicBoolean> active = new ConcurrentHashMap<>();

	private MetricsHttpServer http;

	private Histogram scrapeDuration;
	private Histogram collectorDuration;
	private Counter errors;
	private Gauge backgroundAge;
	private Gauge up;
	private Gauge collectorInfo;

	public Exporter(Platform platform, Config config) {
		this.platform = platform;
		this.config = config;
	}

	@Override
	public MetricRegistry registry() {
		return registry;
	}

	@Override
	public Platform platform() {
		return platform;
	}

	@Override
	public Config config() {
		return config;
	}

	@Override
	public String version() {
		return Version.VALUE;
	}

	@Override
	public void register(Collector c) {
		if (active.containsKey(c)) {
			return;
		}
		if (!config.isCollectorEnabled(c.name(), true)) {
			platform.info("collector " + c.name() + ": disabled in configuration");
			return;
		}
		AtomicBoolean alive = new AtomicBoolean(true);
		active.put(c, alive);
		c.declare(registry);

		if (c.isBackground()) {
			long interval = config.getSeconds("collector." + c.name() + ".interval", c.intervalSeconds());
			lastCollection.put(c.name(), new AtomicLong(0));
			// The flag is checked on every run: neither Bukkit nor Velocity offers a simple way to
			// cancel a task from code that did not create it, and an unloaded collector must stop
			// being called immediately.
			platform.scheduleRepeating(() -> {
				if (alive.get()) {
					collect(c);
				}
			}, interval);
			platform.info("collector " + c.name() + ": background, every " + interval + " s");
		} else {
			onScrape.add(c);
			platform.info("collector " + c.name() + ": on scrape");
		}
		publishInfo(c);
	}

	@Override
	public void unregister(Collector c) {
		AtomicBoolean alive = active.remove(c);
		if (alive == null) {
			return;
		}
		alive.set(false);
		onScrape.remove(c);
		lastCollection.remove(c.name());
		// Rewritten in full: removing a single series would require its exact label values, and
		// republishing the rest costs a few lines.
		collectorInfo.clear();
		active.keySet().forEach(this::publishInfo);
		try {
			c.close();
		} catch (Exception e) {
			platform.error("closing collector " + c.name(), e);
		}
		platform.info("collector " + c.name() + ": unregistered");
	}

	private void publishInfo(Collector c) {
		collectorInfo.set(1, c.name(), c.source(), c.isBackground() ? "background" : "scrape");
	}

	/** @param platformCollectors the platform-specific collectors, supplied by the adapter */
	public void start(List<Collector> platformCollectors) throws Exception {
		declareOwnMetrics();

		// Core collectors run everywhere: the JVM, cgroup and disk have nothing Minecraft-specific,
		// and leaving them out on the proxy would be an unexplained gap.
		register(new JvmCollector());
		register(new CgroupCollector(platform));
		register(new DiskCollector(platform, config));
		platformCollectors.forEach(this::register);

		registry.gauge("build_info",
						"Always 1. The version is in the labels, the Prometheus idiom for publishing "
								+ "a string.",
						"version", "platform", "server_version", "server")
				.set(1, Version.VALUE, platform.type(), platform.serverVersion(),
						platform.serverName());
		up.set(1);

		http = new MetricsHttpServer(platform, config, this::scrape);
		http.start();

		// Last, once everything is in place: from here on collector plugins can register, and they
		// must not find a half-started core.
		VaniaMetricsProvider.set(this);
	}

	public void stop() {
		VaniaMetricsProvider.set(null);
		if (http != null) {
			http.stop();
		}
		active.keySet().forEach(this::unregister);
	}

	/**
	 * Runs one collection, timed and guarded.
	 *
	 * <p>Exceptions do not propagate. A failing collector is counted and skipped: an unreachable
	 * database must not make TPS disappear from Grafana, which is exactly what you look at when
	 * something goes wrong.
	 */
	private void collect(Collector c) {
		long start = System.nanoTime();
		try {
			if (c.needsMainThread()) {
				platform.runOnMainThread(() -> {
					try {
						c.collect(registry);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			} else {
				c.collect(registry);
			}
			AtomicLong t = lastCollection.get(c.name());
			if (t != null) {
				t.set(System.currentTimeMillis());
			}
		} catch (TimeoutException e) {
			// The main thread did not answer within five seconds. Expected during startup, while
			// it loads worlds and plugins: the first scrape hits it, the next ones pass. Seen once,
			// never again.
			//
			// Counted like any other error, so mc_exporter_scrape_errors_total stays honest, but
			// logged without a stack trace: fifty lines of trace for a state that fixes itself bury
			// the real failures.
			errors.inc(c.name());
			platform.warn("collector " + c.name() + ": main thread did not respond within 5 s; "
					+ "expected while the server is still starting");
		} catch (Throwable e) {
			// Throwable: a collector whose target plugin changed version fails with
			// NoSuchMethodError, which is an Error. Counting it beats losing everything.
			errors.inc(c.name());
			platform.error("collector " + c.name(), e);
		} finally {
			collectorDuration.observe((System.nanoTime() - start) / 1e9, c.name());
		}
	}

	/** Called by the HTTP thread. Must return quickly: see {@link Collector}. */
	private String scrape() {
		long start = System.nanoTime();
		for (Collector c : onScrape) {
			collect(c);
		}
		long now = System.currentTimeMillis();
		lastCollection.forEach((name, t) -> {
			long when = t.get();
			backgroundAge.set(when == 0 ? Double.NaN : (now - when) / 1000.0, name);
		});
		String text = registry.render();
		scrapeDuration.observe((System.nanoTime() - start) / 1e9);
		return text;
	}

	private void declareOwnMetrics() {
		scrapeDuration = registry.histogram("exporter_scrape_duration_seconds",
				"Time spent answering a scrape. If this grows, the exporter is the problem.",
				new double[] {0.001, 0.005, 0.010, 0.025, 0.050, 0.100, 0.250, 1.0});
		collectorDuration = registry.histogram("exporter_collector_duration_seconds",
				"Duration of one collection, per collector.",
				new double[] {0.001, 0.005, 0.010, 0.050, 0.100, 0.500, 1.0, 5.0}, "collector");
		errors = registry.counter("exporter_scrape_errors_total",
				"Collections that threw. A failing collector does not stop the others.",
				"collector");
		backgroundAge = registry.gauge("exporter_background_task_age_seconds",
				"Age of a background collector's last successful run. Tells \"nothing changes\" "
						+ "apart from \"nothing is measuring\".",
				"collector");
		up = registry.gauge("exporter_up", "1 once the exporter has finished starting.");
		collectorInfo = registry.gauge("exporter_collector_info",
				"Always 1, one per active collector. source is where its numbers come from: "
						+ "\"core\" for what the server exposes itself, otherwise the plugin name.",
				"collector", "source", "mode");
	}
}
