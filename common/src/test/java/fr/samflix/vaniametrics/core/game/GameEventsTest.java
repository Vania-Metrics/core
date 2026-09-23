package fr.samflix.vaniametrics.core.game;

import static fr.samflix.vaniametrics.core.Rendered.families;
import static fr.samflix.vaniametrics.core.Rendered.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.samflix.vaniametrics.api.MetricRegistry;

class GameEventsTest {

	private final MetricRegistry registry = new MetricRegistry();
	private final GameEvents events = new GameEvents(registry);

	@Test
	void nothingIsPublishedBeforeTheFirstEvent() {
		assertEquals("", registry.render());
	}

	@Test
	void onlyTrackedCommandsKeepTheirName() {
		events.command("/spawn");
		events.command("TP Notch");
		events.command("/whatever arg");
		String text = registry.render();
		assertEquals(1, value(text, "mc_server_commands_total{command=\"spawn\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_commands_total{command=\"tp\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_commands_total{command=\"other\"}").orElseThrow());
	}

	@Test
	void aQuitClosesTheSession() {
		UUID probe = UUID.randomUUID();
		events.join(probe);
		events.quit(probe);
		String text = registry.render();
		assertEquals(1, value(text, "mc_server_connections_total{result=\"join\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_connections_total{result=\"quit\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_session_seconds_count").orElseThrow());
	}

	@Test
	void aQuitWithoutJoinCountsNoSession() {
		events.quit(UUID.randomUUID());
		assertTrue(!families(registry.render()).contains("mc_server_session_seconds"));
	}

	@Test
	void labelsAreLowerCased() {
		events.playerDeath(null);
		events.playerDeath("LAVA");
		events.mobKilledByPlayer("ZOMBIE");
		String text = registry.render();
		assertEquals(1, value(text, "mc_server_deaths_total{cause=\"unknown\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_deaths_total{cause=\"lava\"}").orElseThrow());
		assertEquals(1, value(text, "mc_server_mob_deaths_total{entity_type=\"zombie\"}").orElseThrow());
	}
}
