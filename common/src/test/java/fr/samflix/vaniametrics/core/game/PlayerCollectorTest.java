package fr.samflix.vaniametrics.core.game;

import static fr.samflix.vaniametrics.core.Rendered.families;
import static fr.samflix.vaniametrics.core.Rendered.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.MetricRegistry;

class PlayerCollectorTest {

	private final MetricRegistry registry = new MetricRegistry();

	private String collect(FakeGameServer server) {
		PlayerCollector c = new PlayerCollector(server, Config.of(new Properties(), Map.of()));
		c.declare(registry);
		c.collect(registry);
		return registry.render();
	}

	@Test
	void anEmptyServerPublishesTotalsButNoDistribution() {
		String text = collect(new FakeGameServer());
		assertEquals(0, value(text, "mc_server_players_online").orElseThrow());
		assertEquals(0, value(text, "mc_server_player_statistics{statistic=\"deaths\"}").orElseThrow());
		assertFalse(families(text).contains("mc_server_players_ping_seconds"), text);
		assertFalse(families(text).contains("mc_server_players_by_brand"), text);
		assertFalse(families(text).contains("mc_server_player_ping_seconds"), text);
	}

	@Test
	void anOnlinePlayerGetsDetailAndBreakdowns() {
		String text = collect(FakeGameServer.paper().withPlayer("probe"));
		assertEquals(1, value(text, "mc_server_players_online").orElseThrow());
		assertEquals(1, value(text, "mc_server_players_by_brand{brand=\"vanilla\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_players_by_locale{locale=\"en_us\"}").orElseThrow());
		assertTrue(text.contains("mc_server_player_ping_seconds{player=\"probe\",uuid=\""), text);
	}

	@Test
	void anUnknownBrandIsCountedAsUnknown() {
		String text = collect(FakeGameServer.spigot().withPlayer("probe"));
		assertEquals(1, value(text, "mc_server_players_by_brand{brand=\"unknown\"}").orElseThrow());
	}

	@Test
	void unitsAreConverted() {
		// withPlayer sets every statistic to 40: 40 tenths of a heart, 40 ticks, 35 ms of ping.
		String text = collect(FakeGameServer.paper().withPlayer("probe"));
		assertEquals(4, value(text, "mc_server_player_statistics{statistic=\"damage_dealt\"}").orElseThrow());
		assertEquals(2, value(text, "mc_server_playtime_seconds_total").orElseThrow());
		assertEquals(1, value(text, "mc_server_players_ping_seconds_bucket{le=\"0.05\"}").orElseThrow());
	}
}
