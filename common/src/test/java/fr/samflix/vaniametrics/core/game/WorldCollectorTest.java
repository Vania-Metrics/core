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

class WorldCollectorTest {

	private final MetricRegistry registry = new MetricRegistry();

	private WorldCollector collector(FakeGameServer server, String... keyValues) {
		Properties p = new Properties();
		for (int i = 0; i < keyValues.length; i += 2) {
			p.setProperty(keyValues[i], keyValues[i + 1]);
		}
		WorldCollector c = new WorldCollector(server, Config.of(p, Map.of()));
		c.declare(registry);
		return c;
	}

	@Test
	void runsInTheBackground() {
		WorldCollector c = collector(new FakeGameServer());
		assertTrue(c.isBackground());
		assertEquals(15, c.intervalSeconds());
	}

	@Test
	void countsAreLabelledByWorld() {
		collector(FakeGameServer.paper()).collect(registry);
		String text = registry.render();
		assertEquals(10, value(text, "mc_world_entities{world=\"world\"}").orElseThrow());
		assertEquals(6000, value(text, "mc_world_time_ticks{world=\"world\"}").orElseThrow());
	}

	@Test
	void countsTheLoaderCannotProvideAreAbsent() {
		FakeGameServer server = FakeGameServer.paper();
		server.worldCounts = false;
		collector(server).collect(registry);
		String text = registry.render();
		assertFalse(families(text).contains("mc_world_entities"), text);
		assertFalse(families(text).contains("mc_world_chunks_loaded"), text);
		assertTrue(families(text).contains("mc_world_players"), text);
	}

	@Test
	void entityTypesBelowTheThresholdAreNotPublished() {
		collector(FakeGameServer.paper()).collect(registry);
		String text = registry.render();
		assertTrue(value(text, "mc_world_entities_by_type{world=\"world\",entity_type=\"cow\"}").isPresent());
		assertTrue(value(text, "mc_world_entities_by_type{world=\"world\",entity_type=\"bat\"}").isEmpty());
	}

	@Test
	void aNegativeThresholdSkipsTheEntityPass() {
		FakeGameServer server = FakeGameServer.paper();
		collector(server, "collector.world.entity_type_threshold", "-1").collect(registry);
		assertFalse(server.lastWorldsWithTypes);
		assertFalse(families(registry.render()).contains("mc_world_entities_by_type"));
	}

	@Test
	void anUnloadedWorldDisappears() {
		FakeGameServer server = FakeGameServer.paper();
		server.worldNames.add("nether");
		WorldCollector c = collector(server);
		c.collect(registry);
		assertTrue(registry.render().contains("world=\"nether\""));

		server.worldNames.remove("nether");
		c.collect(registry);
		assertFalse(registry.render().contains("world=\"nether\""), registry.render());
	}
}
