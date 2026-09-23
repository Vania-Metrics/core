package fr.samflix.vaniametrics.core.game;

import static fr.samflix.vaniametrics.core.Rendered.families;
import static fr.samflix.vaniametrics.core.Rendered.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.samflix.vaniametrics.api.MetricRegistry;

class TickCollectorTest {

	private final MetricRegistry registry = new MetricRegistry();

	private String collect(FakeGameServer server) {
		TickCollector c = new TickCollector(server);
		c.declare(registry);
		c.collect(registry);
		return registry.render();
	}

	@Test
	void everythingIsPublishedWhenTheLoaderProvidesIt() {
		String text = collect(FakeGameServer.paper());
		assertTrue(families(text).containsAll(java.util.Set.of("mc_server_tps",
				"mc_server_tick_average_seconds", "mc_server_tick_duration_seconds",
				"mc_server_ticks_total", "mc_server_players_max")), text);
		assertEquals(0.0125, value(text, "mc_server_tick_average_seconds").orElseThrow());
		assertEquals(1200, value(text, "mc_server_ticks_total").orElseThrow());
	}

	@Test
	void tpsIsCappedAtTwenty() {
		FakeGameServer server = FakeGameServer.paper();
		server.tps = new double[] {21.3, 20, 19.5};
		String text = collect(server);
		assertEquals(20, value(text, "mc_server_tps{window=\"1m\"}").orElseThrow());
		assertEquals(19.5, value(text, "mc_server_tps{window=\"15m\"}").orElseThrow());
	}

	@Test
	void missingCapabilitiesMeanMissingFamiliesNotZeros() {
		FakeGameServer server = new FakeGameServer();
		String text = collect(server);
		assertEquals(java.util.Set.of("mc_server_players_max"), families(text), text);
	}

	@Test
	void aTickCounterWithoutDurationsPublishesNoHistogram() {
		String text = collect(FakeGameServer.spigot());
		assertTrue(families(text).contains("mc_server_ticks_total"));
		assertFalse(families(text).contains("mc_server_tick_duration_seconds"));
		assertFalse(families(text).contains("mc_server_tick_average_seconds"));
	}

	@Test
	void eachTickIsObservedOnceAcrossOverlappingBuffers() {
		FakeGameServer server = FakeGameServer.paper();
		server.currentTick = 100;
		server.recentTickDurations = new long[] {1, 2, 3, 4, 5};
		TickCollector c = new TickCollector(server);
		c.declare(registry);
		c.collect(registry);
		assertEquals(5, value(registry.render(), "mc_server_tick_duration_seconds_count").orElseThrow());

		// Two ticks later the buffer has slid by two: only the two new durations count.
		server.currentTick = 102;
		server.recentTickDurations = new long[] {3, 4, 5, 6, 7};
		c.collect(registry);
		assertEquals(7, value(registry.render(), "mc_server_tick_duration_seconds_count").orElseThrow());
	}

	@Test
	void theHistogramIsInSeconds() {
		FakeGameServer server = FakeGameServer.paper();
		server.recentTickDurations = new long[] {60_000_000};
		String text = collect(server);
		// 60 ms: above the 50 ms bucket, inside the 75 ms one.
		assertEquals(0, value(text, "mc_server_tick_duration_seconds_bucket{le=\"0.05\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_tick_duration_seconds_bucket{le=\"0.075\"}").orElseThrow());
	}
}
