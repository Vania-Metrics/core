package fr.samflix.vaniametrics.core.proxy;

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

class ProxyCollectorTest {

	private final MetricRegistry registry = new MetricRegistry();
	private final FakeProxyServer proxy = new FakeProxyServer();
	private final ProxyCollector collector =
			new ProxyCollector(proxy, Config.of(new Properties(), Map.of()));

	private String collect() {
		collector.collect(registry);
		return registry.render();
	}

	@Test
	void anAnsweringBackendIsUpWithItsSlots() {
		collector.declare(registry);
		proxy.backends.add(new ProxyServer.Backend("lobby", 1));
		proxy.answers.put("lobby", 50);
		String text = collect();
		assertEquals(1, value(text, "mc_proxy_backend_up{server=\"lobby\"}").orElseThrow());
		assertEquals(50, value(text, "mc_proxy_backend_max_players{server=\"lobby\"}").orElseThrow());
		assertEquals(1, value(text, "mc_proxy_backend_players{server=\"lobby\"}").orElseThrow());
		assertTrue(value(text, "mc_proxy_backend_ping_seconds{server=\"lobby\"}").orElseThrow() >= 0);
	}

	@Test
	void aSilentBackendIsDownWithNoPing() {
		collector.declare(registry);
		proxy.backends.add(new ProxyServer.Backend("lobby", 0));
		String text = collect();
		assertEquals(0, value(text, "mc_proxy_backend_up{server=\"lobby\"}").orElseThrow());
		assertTrue(Double.isNaN(value(text, "mc_proxy_backend_ping_seconds{server=\"lobby\"}").orElseThrow()));
		assertFalse(families(text).contains("mc_proxy_backend_max_players"), text);
	}

	@Test
	void slotsAreAbsentWhenThePingHasNone() {
		collector.declare(registry);
		proxy.backends.add(new ProxyServer.Backend("lobby", 0));
		proxy.answers.put("lobby", -1);
		assertFalse(families(collect()).contains("mc_proxy_backend_max_players"));
	}

	@Test
	void aRemovedBackendDisappears() {
		collector.declare(registry);
		proxy.backends.add(new ProxyServer.Backend("old", 0));
		collect();
		proxy.backends.clear();
		assertFalse(collect().contains("server=\"old\""));
	}

	@Test
	void brandsAreNormalised() {
		collector.declare(registry);
		proxy.players.add(new ProxyServer.ProxyPlayer(20, "Vanilla"));
		proxy.players.add(new ProxyServer.ProxyPlayer(30, null));
		String text = collect();
		assertEquals(2, value(text, "mc_proxy_players_online").orElseThrow());
		assertEquals(1, value(text, "mc_proxy_players_by_brand{brand=\"vanilla\"}").orElseThrow());
		assertEquals(1, value(text, "mc_proxy_players_by_brand{brand=\"unknown\"}").orElseThrow());
	}

	@Test
	void aFirstConnectionComesFromNone() {
		ProxyEvents events = new ProxyEvents(registry);
		events.serverSwitch(null, "lobby");
		assertEquals(1, value(registry.render(),
				"mc_proxy_server_switches_total{from=\"none\",to=\"lobby\"}").orElseThrow());
	}
}
