package fr.samflix.vaniametrics.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;

import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * What goes through the proxy.
 *
 * <p>{@code mc_proxy_server_switches_total} describes the network: where players come from, where
 * they go, and which server loses them. Only the proxy can see it.
 *
 * <p>The {@code from} and {@code to} labels are bounded by the number of registered servers, which
 * is small and known. That is what makes them acceptable where a player name would not be.
 *
 * <p>{@code priority = Short.MIN_VALUE} rather than {@code order = PostOrder.LAST}, deprecated in
 * Velocity 3.5: numeric priority replaced it, and the lowest runs last. The event is seen as other
 * plugins left it, the equivalent of Bukkit's {@code EventPriority.MONITOR}.
 */
final class ProxyEventListener {

	private final Counter connections;
	private final Counter switches;
	private final Counter kicks;
	private final Counter pings;

	ProxyEventListener(MetricRegistry r) {
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

	@Subscribe(priority = Short.MIN_VALUE)
	public void onPreLogin(PreLoginEvent e) {
		connections.inc("pre_login");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onLogin(LoginEvent e) {
		connections.inc("login");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onDisconnect(DisconnectEvent e) {
		connections.inc("disconnect");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onConnected(ServerConnectedEvent e) {
		String from = e.getPreviousServer()
				.map(s -> s.getServerInfo().getName())
				.orElse("none");
		switches.inc(from, e.getServer().getServerInfo().getName());
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onKicked(KickedFromServerEvent e) {
		kicks.inc(e.getServer().getServerInfo().getName());
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onPing(ProxyPingEvent e) {
		pings.inc();
	}
}
