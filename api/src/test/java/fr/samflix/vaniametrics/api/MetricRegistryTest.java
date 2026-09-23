package fr.samflix.vaniametrics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class MetricRegistryTest {

	private final MetricRegistry registry = new MetricRegistry();

	@Test
	void aNameOutsideTheKnownDomainsIsRefused() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> registry.gauge("players_online", "help"));
		assertTrue(e.getMessage().contains("no known domain"), e.getMessage());
	}

	@Test
	void aCounterMustEndInTotal() {
		assertThrows(IllegalArgumentException.class,
				() -> registry.counter("server_deaths", "help"));
	}

	@Test
	void everyNameGetsThePrefix() {
		registry.gauge("server_tps", "help").set(20);
		assertTrue(registry.render().contains("\nmc_server_tps 20\n"), registry.render());
	}

	@Test
	void declaringTwiceReturnsTheSameInstrument() {
		assertSame(registry.gauge("server_tps", "help"), registry.gauge("server_tps", "other help"));
	}

	@Test
	void aFamilyWithoutSeriesIsNotRendered() {
		registry.gauge("server_tps", "help");
		registry.counter("server_deaths_total", "help", "cause");
		assertEquals("", registry.render());
	}

	@Test
	void helpAndTypeLinesPrecedeTheSamples() {
		registry.counter("server_deaths_total", "Player deaths.", "cause").inc("lava");
		assertEquals("""
				# HELP mc_server_deaths_total Player deaths.
				# TYPE mc_server_deaths_total counter
				mc_server_deaths_total{cause="lava"} 1
				""", registry.render());
	}

	@Test
	void familiesAreSortedByName() {
		registry.gauge("world_entities", "help").set(1);
		registry.gauge("server_tps", "help").set(1);
		registry.gauge("jvm_threads", "help").set(1);
		List<String> order = registry.render().lines()
				.filter(l -> l.startsWith("# TYPE"))
				.map(l -> l.split(" ")[2])
				.toList();
		assertEquals(List.of("mc_jvm_threads", "mc_server_tps", "mc_world_entities"), order);
	}

	@Test
	void labelValuesAreEscaped() {
		registry.gauge("world_players", "help", "world").set(1, "a\\b\"c\nd");
		assertTrue(registry.render().contains("mc_world_players{world=\"a\\\\b\\\"c\\nd\"} 1"),
				registry.render());
	}

	@Test
	void helpKeepsQuotesButEscapesBackslashAndNewline() {
		registry.gauge("server_tps", "say \"hi\"\\\nbye").set(1);
		assertTrue(registry.render().contains("# HELP mc_server_tps say \"hi\"\\\\\\nbye\n"),
				registry.render());
	}

	@Test
	void aWrongNumberOfLabelsIsRefused() {
		Gauge g = registry.gauge("world_players", "help", "world");
		assertThrows(IllegalArgumentException.class, () -> g.set(1));
		assertThrows(IllegalArgumentException.class, () -> g.set(1, "a", "b"));
	}

	@Test
	void numbersUsePrometheusSpelling() {
		assertEquals("NaN", MetricRegistry.number(Double.NaN));
		assertEquals("+Inf", MetricRegistry.number(Double.POSITIVE_INFINITY));
		assertEquals("-Inf", MetricRegistry.number(Double.NEGATIVE_INFINITY));
		assertEquals("20", MetricRegistry.number(20.0));
		assertEquals("-3", MetricRegistry.number(-3.0));
		assertEquals("0.001", MetricRegistry.number(0.001));
		assertEquals("19.95", MetricRegistry.number(19.95));
		assertEquals("1.0E-9", MetricRegistry.number(1e-9));
	}

	@Test
	void numbersIgnoreTheDefaultLocale() {
		Locale saved = Locale.getDefault();
		try {
			Locale.setDefault(Locale.FRANCE);
			registry.gauge("server_tps", "help").set(19.5);
			assertTrue(registry.render().contains("mc_server_tps 19.5\n"), registry.render());
		} finally {
			Locale.setDefault(saved);
		}
	}

	@Test
	void histogramBucketsAreCumulativeAndEndWithInf() {
		Histogram h = registry.histogram("server_tick_duration_seconds", "help",
				new double[] {0.01, 0.05, 0.1});
		// Powers of two, so the sum is exact in binary.
		h.observe(0.0078125);
		h.observe(0.03125);
		h.observe(0.5);
		assertEquals("""
				# HELP mc_server_tick_duration_seconds help
				# TYPE mc_server_tick_duration_seconds histogram
				mc_server_tick_duration_seconds_bucket{le="0.01"} 1
				mc_server_tick_duration_seconds_bucket{le="0.05"} 2
				mc_server_tick_duration_seconds_bucket{le="0.1"} 2
				mc_server_tick_duration_seconds_bucket{le="+Inf"} 3
				mc_server_tick_duration_seconds_sum 0.5390625
				mc_server_tick_duration_seconds_count 3
				""", registry.render());
	}

	@Test
	void labelledHistogramsKeepLabelsOnEveryLine() {
		registry.histogram("exporter_collector_duration_seconds", "help", new double[] {1},
				"collector").observe(0.5, "jvm");
		List<String> samples = registry.render().lines().filter(l -> !l.startsWith("#")).toList();
		assertEquals(List.of(
				"mc_exporter_collector_duration_seconds_bucket{collector=\"jvm\",le=\"1\"} 1",
				"mc_exporter_collector_duration_seconds_bucket{collector=\"jvm\",le=\"+Inf\"} 1",
				"mc_exporter_collector_duration_seconds_sum{collector=\"jvm\"} 0.5",
				"mc_exporter_collector_duration_seconds_count{collector=\"jvm\"} 1"), samples);
	}

	@Test
	void aCounterNeverGoesDown() {
		Counter c = registry.counter("server_deaths_total", "help");
		c.add(5);
		c.add(-2);
		c.mirror(Double.NaN);
		assertTrue(registry.render().contains("mc_server_deaths_total 5\n"), registry.render());
	}

	@Test
	void clearDropsTheSeriesAndTheFamilyDisappears() {
		Gauge g = registry.gauge("world_players", "help", "world");
		g.set(3, "lobby");
		g.clear();
		assertFalse(registry.render().contains("mc_world_players"));
	}
}
