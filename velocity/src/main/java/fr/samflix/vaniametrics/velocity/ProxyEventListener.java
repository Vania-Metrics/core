package fr.samflix.vaniametrics.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;

import fr.samflix.vaniametrics.core.proxy.ProxyEvents;

/**
 * Forwards Velocity events to {@link ProxyEvents}.
 *
 * <p>{@code priority = Short.MIN_VALUE} rather than {@code order = PostOrder.LAST}, deprecated in
 * Velocity 3.5: numeric priority replaced it, and the lowest runs last. The event is seen as other
 * plugins left it, the equivalent of Bukkit's {@code EventPriority.MONITOR}.
 */
final class ProxyEventListener {

	private final ProxyEvents events;

	ProxyEventListener(ProxyEvents events) {
		this.events = events;
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onPreLogin(PreLoginEvent e) {
		events.preLogin();
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onLogin(LoginEvent e) {
		events.login();
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onDisconnect(DisconnectEvent e) {
		events.disconnect();
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onConnected(ServerConnectedEvent e) {
		events.serverSwitch(
				e.getPreviousServer().map(s -> s.getServerInfo().getName()).orElse(null),
				e.getServer().getServerInfo().getName());
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onKicked(KickedFromServerEvent e) {
		events.kickedFrom(e.getServer().getServerInfo().getName());
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onPing(ProxyPingEvent e) {
		events.listPing();
	}
}
