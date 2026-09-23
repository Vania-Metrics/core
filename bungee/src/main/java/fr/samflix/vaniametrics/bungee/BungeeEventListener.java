package fr.samflix.vaniametrics.bungee;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import fr.samflix.vaniametrics.core.proxy.ProxyEvents;

/**
 * Forwards BungeeCord events to {@link ProxyEvents}. {@code EventPriority.HIGHEST} runs last on
 * BungeeCord (there is no MONITOR): the event is seen as other plugins left it.
 */
public final class BungeeEventListener implements Listener {

	private final ProxyEvents events;

	BungeeEventListener(ProxyEvents events) {
		this.events = events;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPreLogin(PreLoginEvent e) {
		events.preLogin();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onLogin(PostLoginEvent e) {
		events.login();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDisconnect(PlayerDisconnectEvent e) {
		events.disconnect();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSwitch(ServerSwitchEvent e) {
		events.serverSwitch(e.getFrom() == null ? null : e.getFrom().getName(),
				e.getPlayer().getServer().getInfo().getName());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onKick(ServerKickEvent e) {
		events.kickedFrom(e.getKickedFrom().getName());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPing(ProxyPingEvent e) {
		events.listPing();
	}
}
