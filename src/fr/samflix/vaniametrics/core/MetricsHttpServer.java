package fr.samflix.vaniametrics.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Platform;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Le point de collecte : {@code GET /metrics}.
 *
 * <p>{@code com.sun.net.httpserver} est dans le JDK depuis Java 6 et suffit largement : on sert un
 * document texte à un client toutes les quinze secondes. Embarquer Jetty ou Netty pour ça
 * ajouterait des mégaoctets et un risque de conflit de classes avec le serveur, qui embarque déjà
 * les siens.
 *
 * <p>SON EXÉCUTEUR EST À LUI. Un fil dédié, qui n'est ni celui du serveur ni celui de
 * l'ordonnanceur : un scrape lent ne doit pouvoir retarder ni un tick, ni une tâche de fond.
 */
final class MetricsHttpServer {

	private static final String TYPE_CONTENU = "text/plain; version=0.0.4; charset=utf-8";

	private final Platform plateforme;
	private final Config config;
	private final Supplier<String> scrape;

	private HttpServer serveur;

	MetricsHttpServer(Platform plateforme, Config config, Supplier<String> scrape) {
		this.plateforme = plateforme;
		this.config = config;
		this.scrape = scrape;
	}

	void demarrer() throws IOException {
		String adresse = config.texte("http.bind", "0.0.0.0");
		int port = config.entier("http.port", 9940);
		String chemin = config.texte("http.path", "/metrics");
		String jeton = config.texte("http.token", "");

		serveur = HttpServer.create(new InetSocketAddress(adresse, port), 4);
		serveur.createContext(chemin, e -> repondre(e, jeton));
		// Une sonde de vivacité qui ne coûte rien, pour un probe Kubernetes.
		serveur.createContext("/healthz", e -> ecrire(e, 200, "ok\n", "text/plain; charset=utf-8"));
		serveur.setExecutor(Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "vania-metrics-http");
			t.setDaemon(true);
			return t;
		}));
		serveur.start();
		plateforme.info("métriques exposées sur http://" + adresse + ":" + port + chemin
				+ (jeton.isEmpty() ? "" : " (jeton exigé)"));
	}

	void arreter() {
		if (serveur != null) {
			// Un délai de zéro : on ferme tout de suite. Les scrapes en cours sont perdus, ce qui
			// est sans conséquence — Prometheus réessaiera dans quinze secondes.
			serveur.stop(0);
		}
	}

	private void repondre(HttpExchange e, String jeton) throws IOException {
		try {
			if (!"GET".equals(e.getRequestMethod())) {
				ecrire(e, 405, "méthode non acceptée\n", "text/plain; charset=utf-8");
				return;
			}
			if (!jeton.isEmpty()) {
				String entete = e.getRequestHeaders().getFirst("Authorization");
				if (entete == null || !entete.equals("Bearer " + jeton)) {
					ecrire(e, 401, "jeton absent ou faux\n", "text/plain; charset=utf-8");
					return;
				}
			}
			ecrire(e, 200, scrape.get(), TYPE_CONTENU);
		} catch (Exception erreur) {
			plateforme.erreur("réponse au scrape", erreur);
			// 500 et pas une page vide : Prometheus doit voir un échec, sinon il enregistre une
			// absence de métriques comme si le serveur n'avait rien à dire.
			ecrire(e, 500, "collecte en échec\n", "text/plain; charset=utf-8");
		}
	}

	private void ecrire(HttpExchange e, int code, String corps, String typeContenu)
			throws IOException {
		byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
		e.getResponseHeaders().set("Content-Type", typeContenu);
		e.sendResponseHeaders(code, octets.length);
		try (OutputStream out = e.getResponseBody()) {
			out.write(octets);
		}
	}
}
