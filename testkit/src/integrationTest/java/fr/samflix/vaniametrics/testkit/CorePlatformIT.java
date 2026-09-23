package fr.samflix.vaniametrics.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.Network;

import fr.samflix.vaniametrics.testkit.Manifest.Claim;

/**
 * The core on every platform its manifest names: one cell per pinned build.
 *
 * <p>The checks are those of the manual smoke tests, now repeatable: the plugin starts, serves
 * {@code /metrics}, publishes what this loader can provide and nothing it cannot, counts a real
 * player joining and leaving, and shuts down cleanly with a clean log.
 */
class CorePlatformIT {

	private static final List<Pattern> ALLOWED_WARNINGS =
			List.of(Pattern.compile(Pattern.quote(LogAudit.STARTUP_TIMEOUT)));

	@TestFactory
	Stream<DynamicTest> platforms() throws IOException {
		Manifest manifest = Manifest.load(Harness.manifest());
		boolean blockingTask = "tested".equals(Harness.status());
		List<DynamicTest> cells = new ArrayList<>();
		for (Manifest.Entry entry : manifest.entries().values()) {
			Loader loader = Loader.byKey(entry.platform())
					.orElseThrow(() -> new IllegalStateException("unknown platform " + entry.platform()));
			if (!Harness.platforms().isEmpty() && !Harness.platforms().contains(loader.key())) {
				continue;
			}
			List<Variant> variants = Variant.of(loader);
			if (variants.isEmpty()) {
				if ((entry.claim() == Claim.YES) == blockingTask) {
					cells.add(Report.skipped(loader.key(), claim(entry.claim()),
							"the harness cannot start this loader yet"));
				}
				continue;
			}
			for (Variant v : variants) {
				boolean blocking = entry.claim() == Claim.YES && !v.exploratory();
				if (blocking != blockingTask) {
					continue;
				}
				ServerContainer[] started = new ServerContainer[1];
				cells.add(Report.cell(v.name(), v.exploratory() ? "exploratory" : claim(entry.claim()),
						() -> started[0] == null ? v.build() : started[0].build(),
						() -> run(v, started)));
			}
		}
		if (cells.isEmpty()) {
			// Gradle fails a test task that runs nothing; say why there is nothing instead.
			cells.add(Report.skipped("none", "-", "no platform selected for this task"));
		}
		return cells.stream();
	}

	@AfterAll
	static void report() {
		Report.write("VaniaMetrics core", Harness.status());
	}

	private static String claim(Claim c) {
		return c == Claim.YES ? "tested" : c.name().toLowerCase(java.util.Locale.ROOT);
	}

	private void run(Variant v, ServerContainer[] started) throws Exception {
		switch (v.loader().family) {
			case BUKKIT -> gameServer(v, started);
			default -> throw new org.opentest4j.TestAbortedException("no scenario for " + v.loader());
		}
	}

	private void gameServer(Variant v, ServerContainer[] started) throws Exception {
		Path jar = Harness.jar(v.loader().family.coreJar());
		try (Network network = Network.newNetwork();
				ServerContainer server = ServerContainer.game(v, Images.SERVER_JAVA21, List.of(jar), Map.of(),
						network, "lobby", "core-" + v.name())) {
			started[0] = server;
			server.start();
			MetricsEndpoint metrics = server.metrics();

			// Who is answering.
			Scrape first = metrics.scrape();
			assertEquals(1, first.value("mc_build_info", "platform", v.loader().key(),
					"version", Harness.version(jar)).orElse(0),
					"mc_build_info:\n" + first.excerpt("mc_build_info"));

			// What an idle server of this loader publishes, once the world collector has run twice.
			Scrape idle = metrics.await("the world collector ran twice", Duration.ofSeconds(60),
					s -> s.value("mc_exporter_collector_duration_seconds_count", "collector", "world")
							.orElse(0) >= 2);
			checkExpected(v.loader().key(), idle);

			// A player joins...
			try (JavaBot bot = JavaBot.join(network, "lobby", ServerContainer.GAME_PORT, "probe",
					Duration.ofSeconds(15))) {
				Scrape online = metrics.await("the bot is counted online", Duration.ofSeconds(30),
						s -> s.value("mc_server_players_online").orElse(0) == 1
								&& s.value("mc_server_connections_total", "result", "join").orElse(0) == 1);
				assertTrue(online.value("mc_server_player_ping_seconds", "player", "probe").isPresent(),
						online.excerpt("mc_server_player_ping_seconds"));
				assertEquals(1, online.sum("mc_server_players_by_locale"), online.excerpt("by_locale"));
				// ...and leaves.
				bot.awaitLeft(Duration.ofSeconds(90));
			}
			Scrape after = metrics.await("the bot is counted gone", Duration.ofSeconds(30),
					s -> s.value("mc_server_players_online").orElse(-1) == 0
							&& s.value("mc_server_connections_total", "result", "quit").orElse(0) == 1);
			assertEquals(1, after.value("mc_server_session_seconds_count").orElse(0),
					after.excerpt("mc_server_session_seconds"));
			assertEquals(0, after.sum("mc_exporter_scrape_errors_total"),
					after.excerpt("mc_exporter_scrape_errors_total"));

			// It stops cleanly, and nothing went wrong on the way.
			assertEquals(0, server.stopGracefully(), "exit code of the server");
			checkLog(server.log());
		}
	}

	/** The plugin started, shut down, and left nothing alarming in between. */
	private static void checkLog(String log) {
		String clean = LogAudit.clean(log);
		assertTrue(clean.contains("serving metrics on http://"), "the plugin never started serving");
		assertTrue(clean.contains("collector jvm: unregistered"), "the plugin was not disabled cleanly");
		List<String> findings = LogAudit.findings(log, ALLOWED_WARNINGS);
		assertEquals(List.of(), findings, "log findings");
	}

	/** Compares a scrape with {@code expected/<loader>.txt}. */
	private static void checkExpected(String loader, Scrape scrape) throws IOException {
		List<String> problems = new ArrayList<>();
		try (InputStream in = CorePlatformIT.class.getResourceAsStream("/expected/" + loader + ".txt")) {
			if (in == null) {
				throw new AssertionError("no expected/" + loader + ".txt: write down what this loader publishes");
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = r.readLine()) != null; ) {
				if (line.startsWith("+") && !scrape.has(line.substring(1))) {
					problems.add("missing " + line.substring(1));
				} else if (line.startsWith("-") && scrape.has(line.substring(1))) {
					problems.add("unexpected " + line.substring(1) + ":\n" + scrape.excerpt(line.substring(1)));
				}
			}
		}
		assertEquals(List.of(), problems, "families published on " + loader);
	}
}
