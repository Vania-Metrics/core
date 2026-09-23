package fr.samflix.vaniametrics.velocity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Le proxy : qui est connecté, où, et quels serveurs répondent.
 *
 * <p>{@code mc_proxy_backend_up} EST LA MÉTRIQUE LA PLUS RENTABLE DU LOT. Elle dit qu'un serveur
 * ne répond plus AVANT qu'un joueur ne s'en plaigne, et c'est la seule vue du réseau qui existe :
 * un serveur tombé ne publie plus rien, donc son propre exportateur ne peut pas le signaler.
 *
 * <p>EN FOND, À CAUSE DU PING. {@code RegisteredServer.ping()} ouvre une connexion et attend une
 * réponse : c'est une opération réseau, avec tout ce que ça implique de latence et de délais. La
 * faire au scrape rendrait la durée du scrape dépendante de la santé des serveurs — exactement ce
 * qu'on cherche à mesurer, ce qui fausserait la mesure.
 */
final class ProxyCollector implements Collector {

	private final ProxyServer proxy;
	private final long delaiPing;

	private Gauge enLigne;
	private Gauge parServeur;
	private Gauge debout;
	private Gauge dureePing;
	private Gauge annonces;
	private Histogram ping;
	private Gauge parMarque;

	ProxyCollector(ProxyServer proxy, Config config) {
		this.proxy = proxy;
		this.delaiPing = config.duree("collector.proxy.ping_timeout", 5);
	}

	@Override
	public String nom() {
		return "proxy";
	}

	@Override
	public boolean enFond() {
		return true;
	}

	@Override
	public long intervalleSecondes() {
		return 15;
	}

	@Override
	public void declarer(MetricRegistry r) {
		enLigne = r.gauge("proxy_players_online", "Joueurs connectés au proxy.");
		parServeur = r.gauge("proxy_backend_players", "Joueurs par serveur d'arrière-plan.", "server");
		debout = r.gauge("proxy_backend_up",
				"1 si le serveur répond au ping, 0 sinon. La seule vue du réseau : un serveur "
						+ "tombé ne publie plus ses propres métriques.",
				"server");
		dureePing = r.gauge("proxy_backend_ping_seconds",
				"Temps de réponse au ping, vu du proxy.", "server");
		annonces = r.gauge("proxy_backend_max_players",
				"Places annoncées par le serveur dans sa réponse au ping.", "server");
		ping = r.histogram("proxy_player_ping_seconds",
				"Distribution du ping des joueurs, entre eux et le proxy.", Histogram.SECONDES_PING);
		parMarque = r.gauge("proxy_players_by_brand",
				"Joueurs par marque de client annoncée.", "brand");
	}

	@Override
	public void relever(MetricRegistry r) {
		enLigne.set(proxy.getPlayerCount());

		parMarque.clear();
		Map<String, Integer> marques = new HashMap<>();
		for (Player j : proxy.getAllPlayers()) {
			ping.observe(j.getPing() / 1000.0);
			String m = j.getClientBrand();
			marques.merge(m == null || m.isBlank() ? "unknown" : m.toLowerCase(Locale.ROOT), 1,
					Integer::sum);
		}
		marques.forEach((m, n) -> parMarque.set(n, m));

		for (RegisteredServer serveur : proxy.getAllServers()) {
			String nom = serveur.getServerInfo().getName();
			parServeur.set(serveur.getPlayersConnected().size(), nom);

			long debut = System.nanoTime();
			try {
				var reponse = serveur.ping().get(delaiPing, TimeUnit.SECONDS);
				debout.set(1, nom);
				dureePing.set((System.nanoTime() - debut) / 1e9, nom);
				reponse.getPlayers().ifPresent(p -> annonces.set(p.getMax(), nom));
			} catch (Exception e) {
				// Toute exception vaut « il ne répond pas » : délai dépassé, connexion refusée,
				// réponse illisible. Distinguer les cas ferait une étiquette de plus pour une
				// information que les journaux portent déjà.
				debout.set(0, nom);
				dureePing.set(Double.NaN, nom);
			}
		}
	}
}
