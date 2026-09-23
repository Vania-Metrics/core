package fr.samflix.vaniametrics.core.proxy;

import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * What goes through the proxy. Each proxy loader forwards its own events here, as the proxy
 * finally decided them (after other plugins).
 *
 * <p>{@code mc_proxy_server_switches_total} describes the network: where players come from, where
 * they go, and which server loses them. Only the proxy can see it. The {@code from} and {@code to}
 * labels are bounded by the number of registered servers, which is small and known.
 */
public final class ProxyEvents {

	private final Counter connections;
	private final Counter switches;
	private final Counter kicks;
	private final Counter pings;

	public ProxyEvents(MetricRegistry r) {
		connections = r.counter("proxy_connections_total",
				"Connection attempts. result = pre_login|login|disconnect.", "result");
		switches = r.counter("proxy_server_switches_total",
				"Moves from one server to another. from = \"none\" on first connection.",
				"from", "to");
		kicks = r.counter("proxy_kicks_from_server_total",
				"Players kicked by a backend server.", "server");
		pings = r.counter("proxy_pings_total",
				"Server list pings: clients LOOKING at the server without joining. A measure of "
						+ "attention, not load.");
	}

	public void preLogin() {
		connections.inc("pre_login");
	}

	public void login() {
		connections.inc("login");
	}

	public void disconnect() {
		connections.inc("disconnect");
	}

	/** @param from the previous server, or {@code null} on first connection */
	public void serverSwitch(String from, String to) {
		switches.inc(from == null ? "none" : from, to);
	}

	public void kickedFrom(String server) {
		kicks.inc(server);
	}

	public void listPing() {
		pings.inc();
	}
}
