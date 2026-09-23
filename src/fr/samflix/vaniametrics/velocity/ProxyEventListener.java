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
 * Ce qui traverse le proxy.
 *
 * <p>{@code mc_proxy_server_switches_total} est celle qui raconte le réseau : d'où les joueurs
 * viennent, où ils vont, et quel serveur les perd. Elle n'a de sens que sur le proxy — aucun
 * serveur d'arrière-plan ne peut la voir.
 *
 * <p>Les étiquettes {@code from} et {@code to} sont bornées par le nombre de serveurs déclarés,
 * qui est petit et connu. C'est ce qui les rend acceptables là où un pseudo ne le serait pas.
 *
 * <p>{@code priority = Short.MIN_VALUE} et non {@code order = PostOrder.LAST}, qui est déprécié
 * dans Velocity 3.5 : la priorité numérique l'a remplacé, et la plus basse passe en dernier. On
 * observe donc l'événement tel que les autres plugins l'ont laissé — c'est le pendant exact de
 * {@code EventPriority.MONITOR} côté Bukkit.
 */
final class ProxyEventListener {

	private final Counter connexions;
	private final Counter changements;
	private final Counter expulsions;
	private final Counter pings;

	ProxyEventListener(MetricRegistry r) {
		connexions = r.counter("proxy_connections_total",
				"Tentatives de connexion. result = pre_login|login|disconnect.", "result");
		changements = r.counter("proxy_server_switches_total",
				"Passages d'un serveur à l'autre. from = « none » à la première connexion.",
				"from", "to");
		expulsions = r.counter("proxy_kicks_from_server_total",
				"Joueurs renvoyés par un serveur d'arrière-plan.", "server");
		pings = r.counter("proxy_pings_total",
				"Pings de la liste des serveurs. C'est le trafic des clients qui REGARDENT le "
						+ "serveur sans s'y connecter — un indicateur d'attention, pas de charge.");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onPreLogin(PreLoginEvent e) {
		connexions.inc("pre_login");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onLogin(LoginEvent e) {
		connexions.inc("login");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onDisconnect(DisconnectEvent e) {
		connexions.inc("disconnect");
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onConnected(ServerConnectedEvent e) {
		String depuis = e.getPreviousServer()
				.map(s -> s.getServerInfo().getName())
				.orElse("none");
		changements.inc(depuis, e.getServer().getServerInfo().getName());
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onKicked(KickedFromServerEvent e) {
		expulsions.inc(e.getServer().getServerInfo().getName());
	}

	@Subscribe(priority = Short.MIN_VALUE)
	public void onPing(ProxyPingEvent e) {
		pings.inc();
	}
}
