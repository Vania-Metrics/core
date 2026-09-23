package fr.samflix.vaniametrics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class PlayerSeriesTest {

	private static final PlayerRef ALICE = new PlayerRef("uuid-a", "alice");
	private static final PlayerRef BOB = new PlayerRef("uuid-b", "bob");
	private static final PlayerRef CAROL = new PlayerRef("uuid-c", "carol");

	private final MetricRegistry registry = new MetricRegistry();

	private PlayerSeries series(String... keyValues) {
		Properties p = new Properties();
		for (int i = 0; i < keyValues.length; i += 2) {
			p.setProperty(keyValues[i], keyValues[i + 1]);
		}
		return new PlayerSeries(registry, Config.of(p, Map.of()));
	}

	@Test
	void everyoneIsPublishedUnderTheCap() {
		Collection<PlayerRef> selected = series().select(List.of(ALICE, BOB));
		assertEquals(List.of(ALICE, BOB), List.copyOf(selected));
		assertTrue(registry.render().contains("mc_exporter_player_series_dropped 0\n"));
	}

	@Test
	void theCapIsAppliedAndAnnounced() {
		Collection<PlayerRef> selected =
				series("collector.players.max_series", "2").select(List.of(ALICE, BOB, CAROL));
		assertEquals(2, selected.size());
		assertTrue(registry.render().contains("mc_exporter_player_series_dropped 1\n"));
	}

	@Test
	void disabledPublishesNobodyAndSaysSo() {
		PlayerSeries s = series("collector.players.per_player", "false");
		assertFalse(s.isEnabled());
		assertTrue(s.select(List.of(ALICE, BOB)).isEmpty());
		assertTrue(registry.render().contains("mc_exporter_player_series_dropped 2\n"));
	}

	@Test
	void aPlayerWhoLeftLosesTheirSeries() {
		PlayerSeries s = series();
		Gauge ping = registry.gauge("server_player_ping_seconds", "help", "player", "uuid");
		s.select(List.of(ALICE, BOB), ping);
		ping.set(0.1, ALICE.labels());
		ping.set(0.2, BOB.labels());

		s.select(List.of(ALICE), ping);
		assertFalse(registry.render().contains("bob"), registry.render());
	}

	@Test
	void anUnchangedSetKeepsTheSeries() {
		PlayerSeries s = series();
		Gauge ping = registry.gauge("server_player_ping_seconds", "help", "player", "uuid");
		s.select(List.of(ALICE), ping);
		ping.set(0.1, ALICE.labels());

		s.select(List.of(ALICE), ping);
		assertTrue(registry.render().contains("alice"), registry.render());
	}

	@Test
	void labelsArePlayerThenUuid() {
		assertEquals(List.of("alice", "uuid-a", "deaths"), List.of(ALICE.labels("deaths")));
		assertEquals(new PlayerRef("", ""), new PlayerRef(null, null));
	}
}
