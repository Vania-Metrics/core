package fr.samflix.vaniametrics.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.opentest4j.TestAbortedException;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import fr.samflix.vaniametrics.testkit.Manifest.Claim;

/**
 * The same checks for every collector, on every platform its manifest names.
 *
 * <p>A collector repository only declares {@code class SmokeIT extends CollectorSmokeTest {}}; what
 * to install comes from {@code collector-test.yml}, where to run from {@code compatibility.yml}.
 *
 * <p>A cell passes when, on a real server with the core, the target plugin and the collector:
 * <ol>
 *   <li>every declared collector is registered ({@code mc_exporter_collector_info});
 *   <li>and has actually run ({@code mc_exporter_collector_duration_seconds_count} ≥ 1): being
 *       listed proves nothing about being called;
 *   <li>its error counter does not move over two collection intervals, counted from after
 *       startup, when a scrape may legitimately time out on a busy main thread;
 *   <li>the log holds no stack trace through our code and no warning from our loggers;
 *   <li>the server stops on its own, and each collector logs that it was unregistered;
 *   <li>the target plugin installed is the version the manifest says the collector is compiled
 *       against.
 * </ol>
 */
public abstract class CollectorSmokeTest {

	/** Background collectors run this often during the test, instead of every 30 to 300 s. */
	private static final int INTERVAL_SECONDS = 2;

	@TestFactory
	Stream<DynamicTest> platforms() throws IOException {
		Manifest manifest = Manifest.load(Harness.manifest());
		CollectorTestConfig config = CollectorTestConfig.load(Harness.projectDir().resolve("collector-test.yml"));
		boolean blockingTask = "tested".equals(Harness.status());
		List<DynamicTest> cells = new ArrayList<>();
		for (Manifest.Entry entry : manifest.entries().values()) {
			Loader loader = Loader.byKey(entry.platform())
					.orElseThrow(() -> new IllegalStateException("unknown platform " + entry.platform()));
			if (!Harness.platforms().isEmpty() && !Harness.platforms().contains(loader.key())) {
				continue;
			}
			String claim = entry.claim().name().toLowerCase(Locale.ROOT);
			boolean runnable = entry.claim() != Claim.NO
					|| (Harness.includeUnsupported() && runsTarget(entry));
			boolean blocking = entry.claim() == Claim.YES;
			if (!runnable) {
				// Listed in the untested report only, so each platform appears once overall.
				if (!blockingTask) {
					cells.add(Report.skipped(loader.key(), claim, reason(entry)));
				}
				continue;
			}
			if (blocking != blockingTask) {
				continue;
			}
			List<Variant> variants = Variant.of(loader);
			if (variants.isEmpty()) {
				cells.add(Report.skipped(loader.key(), claim, "the harness cannot start this loader yet"));
				continue;
			}
			for (Variant v : variants) {
				ServerContainer[] started = new ServerContainer[1];
				cells.add(Report.cell(v.name(), v.exploratory() ? "exploratory" : claim,
						() -> started[0] == null || started[0].build() == null ? v.build() : started[0].build(),
						() -> run(v, manifest, config, started)));
			}
		}
		if (cells.isEmpty()) {
			cells.add(Report.skipped("none", "-", "no platform selected for this task"));
		}
		return cells.stream();
	}

	@AfterAll
	static void report() {
		Report.write(Harness.projectDir().toAbsolutePath().normalize().getFileName().toString(), Harness.status());
	}

	private static boolean runsTarget(Manifest.Entry entry) {
		return entry.plugin().equals("yes") || entry.plugin().equals("bundled");
	}

	private static String reason(Manifest.Entry entry) {
		if (!runsTarget(entry)) {
			return "the target plugin does not run here (" + entry.plugin() + ")";
		}
		return "the collector does not run here yet; -Pvania.it.all runs it anyway";
	}

	private void run(Variant v, Manifest manifest, CollectorTestConfig config, ServerContainer[] started)
			throws Exception {
		Loader.Family family = v.loader().family;
		if (family != Loader.Family.BUKKIT && family != Loader.Family.VELOCITY && family != Loader.Family.BUNGEE) {
			throw new TestAbortedException("no collector scenario for " + v.loader() + " yet");
		}
		// Downloads first: a bad digest fails here, before any container starts.
		List<Path> targets = config.pluginsFor(v.loader()).stream()
				.map(d -> Downloads.fetch(d.url(), d.sha512()))
				.toList();
		checkTargetVersion(manifest, v, targets);

		List<Path> jars = new ArrayList<>();
		jars.add(Harness.jar(family.coreJar()));
		jars.add(collectorJar());
		jars.addAll(targets);

		Map<String, String> env = new LinkedHashMap<>(config.env());
		for (String name : config.collectors()) {
			env.put("VANIA_METRICS_COLLECTOR_" + name.toUpperCase(Locale.ROOT) + "_INTERVAL",
					String.valueOf(INTERVAL_SECONDS));
		}
		String cell = Harness.projectDir().toAbsolutePath().normalize().getFileName() + "-" + v.name();

		try (Network network = Network.newNetwork();
				ServerContainer backend = family.isProxy()
						? ServerContainer.game(Variant.of(Loader.PAPER).get(0), Images.SERVER_JAVA21, List.of(),
								Map.of(), network, "lobby", cell + "-lobby")
						: null;
				ServerContainer server = family.isProxy()
						? ServerContainer.proxy(v, jars, env, network, "proxy", cell)
						: ServerContainer.game(v, gameImage(config), jars, gameEnv(env, config), network, "lobby",
								cell)) {
			started[0] = server;
			if (config.startupTimeout() != null) {
				server.withStartupTimeout(config.startupTimeout());
			}
			if (backend != null) {
				// A proxy without a backend runs, but a collector reading its players or servers
				// would then only ever see empty lists.
				backend.startServer();
			}
			server.start();
			MetricsEndpoint metrics = server.metrics();

			Scrape ready = metrics.await("every collector is registered and has run", Duration.ofSeconds(90),
					s -> config.collectors().stream().allMatch(c ->
							s.value("mc_exporter_collector_info", "collector", c).orElse(0) == 1
									&& s.value("mc_exporter_collector_duration_seconds_count", "collector", c)
											.orElse(0) >= 1));
			Map<String, Double> errorsBefore = errors(ready, config.collectors());
			double runsBefore = runs(ready, config.collectors());

			Thread.sleep(Duration.ofSeconds(2L * INTERVAL_SECONDS + 1));
			Scrape later = metrics.await("the collectors ran again", Duration.ofSeconds(30),
					s -> runs(s, config.collectors()) > runsBefore);
			assertEquals(errorsBefore, errors(later, config.collectors()),
					"collection errors after startup:\n" + later.excerpt("mc_exporter_scrape_errors_total"));

			long code = server.stopGracefully();
			// A proxy may exit on the signal itself (143) once its shutdown hooks ran; the log says
			// whether the plugins were disabled cleanly.
			assertTrue(code == 0 || (family.isProxy() && (code == 130 || code == 143)),
					"exit code of the server: " + code);
			String log = LogAudit.clean(server.log());
			assertTrue(log.contains("serving metrics on http://"), "the core never started serving");
			if (v.loader().disablesPluginsOnStop()) {
				for (String c : config.collectors()) {
					assertTrue(log.contains("collector " + c + ": unregistered"),
							"collector " + c + " was not unregistered on shutdown");
				}
			}
			List<Pattern> allowed = new ArrayList<>(config.allowLogs());
			allowed.add(Pattern.compile(Pattern.quote(LogAudit.STARTUP_TIMEOUT)));
			assertEquals(List.of(), LogAudit.findings(server.log(), allowed), "log findings");
		}
	}

	/** A game server runs on the Java the collector's target needs; itzg picks it by image. */
	private static DockerImageName gameImage(CollectorTestConfig config) {
		return config.runtime().equals("java25") ? Images.SERVER_JAVA25 : Images.SERVER_JAVA21;
	}

	private static Map<String, String> gameEnv(Map<String, String> env, CollectorTestConfig config) {
		Map<String, String> out = new LinkedHashMap<>(env);
		if (!config.memory().isEmpty()) {
			out.put("MEMORY", config.memory());
		}
		if (!config.jvmArgs().isEmpty()) {
			out.put("JVM_OPTS", String.join(" ", config.jvmArgs()));
		}
		return out;
	}

	private static Path collectorJar() {
		return Harness.jars().stream()
				.filter(p -> p.getFileName().toString().startsWith("vania-metrics-collector-"))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no collector jar among " + Harness.jars()));
	}

	private static Map<String, Double> errors(Scrape s, List<String> collectors) {
		Map<String, Double> out = new LinkedHashMap<>();
		for (String c : collectors) {
			out.put(c, s.sum("mc_exporter_scrape_errors_total", "collector", c));
		}
		return out;
	}

	private static double runs(Scrape s, List<String> collectors) {
		double n = 0;
		for (String c : collectors) {
			n += s.sum("mc_exporter_collector_duration_seconds_count", "collector", c);
		}
		return n;
	}

	/**
	 * The first jar of the platform's list is the target plugin; its version must be the one the
	 * manifest names. Nothing to check when the plugin is built into the server (spark on Paper),
	 * or when the manifest names an API rather than a plugin version ("spark-api 0.1-…").
	 */
	private static void checkTargetVersion(Manifest manifest, Variant v, List<Path> targets) throws IOException {
		Manifest.Entry entry = manifest.entries().get(v.loader().key());
		Matcher leading = Pattern.compile("^(\\d[\\w.+-]*)").matcher(manifest.plugin("compiled-against"));
		if (targets.isEmpty() || entry == null || entry.plugin().equals("bundled") || !leading.find()) {
			return;
		}
		String expected = leading.group(1);
		String actual = pluginVersion(targets.get(0));
		assertTrue(expected.startsWith(actual) || actual.startsWith(expected),
				targets.get(0).getFileName() + " is version " + actual + ", but the manifest says the collector is "
						+ "compiled against " + expected);
	}

	private static final Pattern VERSION = Pattern.compile("(?m)^version:\\s*['\"]?([^'\"\\s]+)");

	/** The {@code version:} of a plugin jar's descriptor, whichever the platform. */
	static String pluginVersion(Path jar) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			for (String descriptor : List.of("paper-plugin.yml", "plugin.yml", "bungee.yml")) {
				ZipEntry e = zip.getEntry(descriptor);
				if (e != null) {
					try (InputStream in = zip.getInputStream(e)) {
						Matcher m = VERSION.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
						if (m.find()) {
							return m.group(1);
						}
					}
				}
			}
			ZipEntry velocity = zip.getEntry("velocity-plugin.json");
			if (velocity != null) {
				try (InputStream in = zip.getInputStream(velocity)) {
					Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
							.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
					if (m.find()) {
						return m.group(1);
					}
				}
			}
		}
		return "?";
	}
}
