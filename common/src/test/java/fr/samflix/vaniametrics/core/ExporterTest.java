package fr.samflix.vaniametrics.core;

import static fr.samflix.vaniametrics.core.Rendered.families;
import static fr.samflix.vaniametrics.core.Rendered.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Version;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;

class ExporterTest {

	@TempDir
	Path dataDirectory;

	private FakePlatform platform;
	private Exporter exporter;

	/** A collector that counts its calls, and fails on demand. */
	private static final class Probe implements Collector {
		final String name;
		final boolean background;
		final boolean mainThread;
		final AtomicInteger calls = new AtomicInteger();
		volatile RuntimeException failure;

		Probe(String name, boolean background) {
			this(name, background, false);
		}

		Probe(String name, boolean background, boolean mainThread) {
			this.name = name;
			this.background = background;
			this.mainThread = mainThread;
		}

		@Override
		public boolean needsMainThread() {
			return mainThread;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String source() {
			return "Probe";
		}

		@Override
		public boolean isBackground() {
			return background;
		}

		@Override
		public void declare(MetricRegistry r) {
			r.gauge("server_probe_calls", "test", "collector");
		}

		@Override
		public void collect(MetricRegistry r) {
			calls.incrementAndGet();
			RuntimeException f = failure;
			if (f != null) {
				throw f;
			}
		}
	}

	private Exporter start(List<Collector> collectors, String... keyValues) throws Exception {
		Properties p = new Properties();
		p.setProperty("http.bind", "127.0.0.1");
		p.setProperty("http.port", "0");
		p.setProperty("collector.disk.paths", dataDirectory.toString());
		for (int i = 0; i < keyValues.length; i += 2) {
			p.setProperty(keyValues[i], keyValues[i + 1]);
		}
		platform = new FakePlatform(dataDirectory);
		exporter = new Exporter(platform, Config.of(p, Map.of()));
		exporter.start(collectors);
		return exporter;
	}

	@AfterEach
	void stop() {
		if (exporter != null) {
			exporter.stop();
		}
	}

	@Test
	void startPublishesBuildInfoAndUp() throws Exception {
		String text = start(List.of()).scrape();
		assertEquals(1, value(text, "mc_exporter_up").orElseThrow());
		assertEquals(1, value(text, "mc_build_info{version=\"" + Version.VALUE
				+ "\",platform=\"paper\",server_version=\"1.21.11-test\",server=\"lobby\"}").orElseThrow());
	}

	@Test
	void theProviderIsSetOnceStartedAndClearedOnStop() throws Exception {
		start(List.of());
		assertSame(exporter, VaniaMetricsProvider.get());
		exporter.stop();
		exporter = null;
		assertTrue(VaniaMetricsProvider.find().isEmpty());
	}

	@Test
	void everyActiveCollectorIsListedWithItsSourceAndMode() throws Exception {
		String text = start(List.of(new Probe("probe", false), new Probe("slow", true))).scrape();
		assertEquals(1, value(text,
				"mc_exporter_collector_info{collector=\"probe\",source=\"Probe\",mode=\"scrape\"}").orElseThrow());
		assertEquals(1, value(text,
				"mc_exporter_collector_info{collector=\"slow\",source=\"Probe\",mode=\"background\"}").orElseThrow());
		assertEquals(1, value(text,
				"mc_exporter_collector_info{collector=\"jvm\",source=\"core\",mode=\"scrape\"}").orElseThrow());
	}

	@Test
	void aFailingCollectorIsCountedAndTheOthersStillRun() throws Exception {
		Probe bad = new Probe("bad", false);
		Probe good = new Probe("good", false);
		bad.failure = new IllegalStateException("boom");
		String text = start(List.of(bad, good)).scrape();
		assertEquals(1, good.calls.get());
		assertEquals(1, value(text, "mc_exporter_scrape_errors_total{collector=\"bad\"}").orElseThrow());
		assertTrue(platform.errors.stream().anyMatch(e -> e.startsWith("collector bad")), platform.errors.toString());
	}

	@Test
	void aMainThreadTimeoutIsAWarningNotAnError() throws Exception {
		start(List.of(new Probe("main", false, true)));
		platform.failMainThread(new TimeoutException());
		String text = exporter.scrape();
		assertEquals(1, value(text, "mc_exporter_scrape_errors_total{collector=\"main\"}").orElseThrow());
		assertTrue(platform.warnings.stream().anyMatch(w -> w.contains("did not respond")));
		assertTrue(platform.errors.isEmpty(), platform.errors.toString());
	}

	@Test
	void backgroundCollectorsRunOnTheirScheduleNotOnScrape() throws Exception {
		Probe slow = new Probe("slow", true);
		start(List.of(slow));
		String before = exporter.scrape();
		assertEquals(0, slow.calls.get());
		assertTrue(Double.isNaN(value(before,
				"mc_exporter_background_task_age_seconds{collector=\"slow\"}").orElseThrow()));

		platform.runBackgroundTasks();
		assertEquals(1, slow.calls.get());
		assertFalse(Double.isNaN(value(exporter.scrape(),
				"mc_exporter_background_task_age_seconds{collector=\"slow\"}").orElseThrow()));
	}

	@Test
	void everyRunIsTimedPerCollector() throws Exception {
		Probe probe = new Probe("probe", false);
		start(List.of(probe));
		exporter.scrape();
		String text = exporter.scrape();
		assertEquals(2, value(text,
				"mc_exporter_collector_duration_seconds_count{collector=\"probe\"}").orElseThrow());
	}

	@Test
	void scrapeDurationAppearsFromTheSecondScrape() throws Exception {
		start(List.of());
		// Rendered before it is recorded: the first scrape cannot report its own duration.
		assertFalse(families(exporter.scrape()).contains("mc_exporter_scrape_duration_seconds"));
		assertTrue(families(exporter.scrape()).contains("mc_exporter_scrape_duration_seconds"));
	}

	@Test
	void registeringTwiceDoesNothing() throws Exception {
		Probe probe = new Probe("probe", false);
		start(List.of(probe));
		exporter.register(probe);
		exporter.scrape();
		assertEquals(1, probe.calls.get());
	}

	@Test
	void aCollectorSwitchedOffInConfigurationIsNotRegistered() throws Exception {
		Probe probe = new Probe("probe", false);
		String text = start(List.of(probe), "collector.probe", "false").scrape();
		assertEquals(0, probe.calls.get());
		assertFalse(text.contains("collector=\"probe\""), text);
		assertTrue(platform.infos.contains("collector probe: disabled in configuration"));
	}

	@Test
	void unregisterStopsTheCollectorAndDropsItsInfo() throws Exception {
		Probe slow = new Probe("slow", true);
		Probe probe = new Probe("probe", false);
		start(List.of(slow, probe));
		exporter.unregister(slow);
		exporter.unregister(probe);

		platform.runBackgroundTasks();
		String text = exporter.scrape();
		assertEquals(0, slow.calls.get());
		assertEquals(0, probe.calls.get());
		assertFalse(text.contains("collector=\"slow\",source"), text);
		assertTrue(text.contains("collector=\"jvm\",source"), text);
		assertTrue(platform.infos.contains("collector slow: unregistered"));
	}

	@Test
	void theConfiguredIntervalWins() throws Exception {
		start(List.of(new Probe("slow", true)), "collector.slow.interval", "2");
		assertTrue(platform.scheduled.stream().anyMatch(s -> s.intervalSeconds() == 2));
		assertTrue(platform.infos.contains("collector slow: background, every 2 s"));
	}
}
