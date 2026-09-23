package fr.samflix.vaniametrics.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ScrapeTest {

	private static final String TEXT = """
			# HELP mc_server_tps Ticks per second.
			# TYPE mc_server_tps gauge
			mc_server_tps{window="1m"} 20
			mc_server_tps{window="5m"} 19.95
			# HELP mc_server_players_online Online players.
			# TYPE mc_server_players_online gauge
			mc_server_players_online 1
			# TYPE mc_server_player_ping_seconds gauge
			mc_server_player_ping_seconds{player="pro\\"be",uuid="u-1"} 0.035
			# TYPE mc_proxy_backend_ping_seconds gauge
			mc_proxy_backend_ping_seconds{server="lobby"} NaN
			# TYPE mc_server_tick_duration_seconds histogram
			mc_server_tick_duration_seconds_bucket{le="0.05"} 3
			mc_server_tick_duration_seconds_bucket{le="+Inf"} 4
			mc_server_tick_duration_seconds_count 4
			""";

	private final Scrape scrape = Scrape.parse(TEXT);

	@Test
	void familiesComeFromTheTypeLines() {
		assertEquals(Set.of("mc_server_tps", "mc_server_players_online", "mc_server_player_ping_seconds",
				"mc_proxy_backend_ping_seconds", "mc_server_tick_duration_seconds"), scrape.families());
		assertFalse(scrape.has("mc_server_tick_duration_seconds_count"));
	}

	@Test
	void valuesAreFoundByNameAndLabels() {
		assertEquals(19.95, scrape.value("mc_server_tps", "window", "5m").orElseThrow());
		assertEquals(1, scrape.value("mc_server_players_online").orElseThrow());
		assertEquals(4, scrape.value("mc_server_tick_duration_seconds_bucket", "le", "+Inf").orElseThrow());
		assertTrue(scrape.value("mc_server_tps", "window", "1h").isEmpty());
	}

	@Test
	void escapedLabelValuesAreDecoded() {
		Scrape.Sample s = scrape.samples("mc_server_player_ping_seconds").get(0);
		assertEquals(Map.of("player", "pro\"be", "uuid", "u-1"), s.labels());
		assertEquals(0.035, s.value());
	}

	@Test
	void specialValuesParse() {
		assertTrue(Double.isNaN(scrape.value("mc_proxy_backend_ping_seconds").orElseThrow()));
		assertEquals(Double.POSITIVE_INFINITY, Scrape.parse("# TYPE mc_x_y gauge\nmc_x_y +Inf\n")
				.value("mc_x_y").orElseThrow());
	}

	@Test
	void sumAddsMatchingSeries() {
		assertEquals(39.95, scrape.sum("mc_server_tps"), 1e-9);
		assertEquals(0, scrape.sum("mc_absent_total"));
	}
}
