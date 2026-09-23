package fr.samflix.vaniametrics.core.proxy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * The proxy: who is connected, where, and which backends respond.
 *
 * <p>{@code mc_proxy_backend_up} is the most valuable metric here. It shows a backend is down
 * before a player complains, and it is the only view from outside: a server that is down publishes
 * nothing, so its own exporter cannot report it.
 *
 * <p>Runs in the background because of the ping, which opens a connection and waits for an
 * answer; doing that on scrape would make scrape duration depend on backend health, the very
 * thing being measured.
 */
public final class ProxyCollector implements Collector {

	private final ProxyServer proxy;
	private final long pingTimeout;

	private Gauge online;
	private Gauge perBackend;
	private Gauge backendUp;
	private Gauge pingDuration;
	private Gauge advertisedMax;
	private Histogram ping;
	private Gauge byBrand;

	public ProxyCollector(ProxyServer proxy, Config config) {
		this.proxy = proxy;
		this.pingTimeout = config.getSeconds("collector.proxy.ping_timeout", 5);
	}

	@Override
	public String name() {
		return "proxy";
	}

	@Override
	public boolean isBackground() {
		return true;
	}

	@Override
	public long intervalSeconds() {
		return 15;
	}

	@Override
	public void declare(MetricRegistry r) {
		online = r.gauge("proxy_players_online", "Players connected to the proxy.");
		perBackend = r.gauge("proxy_backend_players", "Players per backend server.", "server");
		backendUp = r.gauge("proxy_backend_up",
				"1 if the backend answers the ping, 0 otherwise. The only outside view: a server "
						+ "that is down no longer publishes its own metrics.",
				"server");
		pingDuration = r.gauge("proxy_backend_ping_seconds",
				"Backend ping response time, as seen from the proxy.", "server");
		advertisedMax = r.gauge("proxy_backend_max_players",
				"Player slots advertised by the backend in its ping response.", "server");
		ping = r.histogram("proxy_player_ping_seconds",
				"Ping distribution between players and the proxy.", Histogram.PING_SECONDS);
		byBrand = r.gauge("proxy_players_by_brand",
				"Players by advertised client brand.", "brand");
	}

	@Override
	public void collect(MetricRegistry r) {
		online.set(proxy.playerCount());

		byBrand.clear();
		Map<String, Integer> brands = new HashMap<>();
		for (ProxyServer.ProxyPlayer p : proxy.players()) {
			ping.observe(p.pingMillis() / 1000.0);
			String b = p.brand();
			brands.merge(b == null || b.isBlank() ? "unknown" : b.toLowerCase(Locale.ROOT), 1,
					Integer::sum);
		}
		brands.forEach((b, n) -> byBrand.set(n, b));

		// Backends can be removed at runtime; clear so a removed one does not stay "up" forever.
		perBackend.clear();
		backendUp.clear();
		pingDuration.clear();
		advertisedMax.clear();
		for (ProxyServer.Backend backend : proxy.backends()) {
			String name = backend.name();
			perBackend.set(backend.players(), name);

			long start = System.nanoTime();
			try {
				int max = proxy.ping(name, pingTimeout);
				backendUp.set(1, name);
				pingDuration.set((System.nanoTime() - start) / 1e9, name);
				if (max >= 0) {
					advertisedMax.set(max, name);
				}
			} catch (Exception e) {
				// Any exception means "not answering": timeout, connection refused, unreadable
				// response. Telling them apart would add a label for information the logs already
				// have.
				backendUp.set(0, name);
				pingDuration.set(Double.NaN, name);
			}
		}
	}
}
