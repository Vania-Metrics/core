package fr.samflix.vaniametrics.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Platform;
import fr.samflix.vaniametrics.api.Version;
import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;
import fr.samflix.vaniametrics.core.collector.CgroupCollector;
import fr.samflix.vaniametrics.core.collector.DiskCollector;
import fr.samflix.vaniametrics.core.collector.JvmCollector;

/**
 * L'orchestrateur, et l'implémentation de l'API publique.
 *
 * <p>Il tient le registre, les collecteurs et le serveur HTTP. Les adaptateurs le démarrent ; les
 * modules — qui sont des plugins à part, dans leurs propres jars — s'y enregistrent par
 * {@link VaniaMetrics}.
 *
 * <p>L'ENREGISTREMENT EST DYNAMIQUE, et c'est ce qui change tout par rapport à une liste figée au
 * démarrage : un module activé après le noyau est déclaré et programmé sur-le-champ, et un module
 * déchargé se retire proprement. Sans quoi le scrape continuerait d'appeler du code dont le
 * classloader vient de disparaître.
 */
public final class Exporter implements VaniaMetrics {

	private final Platform plateforme;
	private final Config config;
	private final MetricRegistry registre = new MetricRegistry();

	/** CopyOnWrite : lue par le fil HTTP à chaque scrape, écrite quand un module arrive. */
	private final List<Collector> auScrape = new CopyOnWriteArrayList<>();

	private final Map<String, AtomicLong> dernierReleve = new ConcurrentHashMap<>();
	private final Map<Collector, AtomicBoolean> actifs = new ConcurrentHashMap<>();

	private MetricsHttpServer http;

	private Histogram dureeScrape;
	private Histogram dureeCollecteur;
	private Counter erreurs;
	private Gauge ageFond;
	private Gauge debout;
	private Gauge inventaire;

	public Exporter(Platform plateforme, Config config) {
		this.plateforme = plateforme;
		this.config = config;
	}

	// ---------------------------------------------------------------- API publique

	@Override
	public MetricRegistry registre() {
		return registre;
	}

	@Override
	public Platform plateforme() {
		return plateforme;
	}

	@Override
	public Config config() {
		return config;
	}

	@Override
	public String version() {
		return Version.VALEUR;
	}

	@Override
	public void enregistrer(Collector c) {
		if (actifs.containsKey(c)) {
			return;
		}
		if (!config.collecteurActif(c.nom(), true)) {
			plateforme.info("collecteur " + c.nom() + " — désactivé par la configuration");
			return;
		}
		AtomicBoolean vivant = new AtomicBoolean(true);
		actifs.put(c, vivant);
		c.declarer(registre);

		if (c.enFond()) {
			long intervalle = config.duree("collector." + c.nom() + ".interval", c.intervalleSecondes());
			dernierReleve.put(c.nom(), new AtomicLong(0));
			// Le drapeau est lu à CHAQUE exécution : ni Bukkit ni Velocity ne donnent un moyen
			// simple d'annuler une tâche depuis un code qui ne l'a pas créée, et un module
			// déchargé doit cesser d'être appelé immédiatement.
			plateforme.repeter(() -> {
				if (vivant.get()) {
					relever(c);
				}
			}, intervalle);
			plateforme.info("collecteur " + c.nom() + " — en fond, toutes les " + intervalle + " s");
		} else {
			auScrape.add(c);
			plateforme.info("collecteur " + c.nom() + " — au scrape");
		}
		inventaire.set(1, c.nom(), c.origine(), c.enFond() ? "background" : "scrape");
	}

	@Override
	public void retirer(Collector c) {
		AtomicBoolean vivant = actifs.remove(c);
		if (vivant == null) {
			return;
		}
		vivant.set(false);
		auScrape.remove(c);
		dernierReleve.remove(c.nom());
		// L'inventaire est réécrit en entier : retirer UNE série demanderait de connaître ses
		// étiquettes exactes, et republier le reste coûte trois lignes.
		inventaire.clear();
		actifs.keySet().forEach(
				a -> inventaire.set(1, a.nom(), a.origine(), a.enFond() ? "background" : "scrape"));
		try {
			c.fermer();
		} catch (Exception e) {
			plateforme.erreur("fermeture du collecteur " + c.nom(), e);
		}
		plateforme.info("collecteur " + c.nom() + " — retiré");
	}

	// ---------------------------------------------------------------- cycle de vie

	/** @param natifs les collecteurs de la plateforme, fournis par l'adaptateur. */
	public void demarrer(List<Collector> natifs) throws Exception {
		declarerSesPropresMetriques();

		// Les collecteurs du noyau tournent partout : la JVM, le cgroup et le disque n'ont rien
		// de spécifique à Minecraft, et leur absence côté proxy serait un trou inexplicable.
		enregistrer(new JvmCollector());
		enregistrer(new CgroupCollector(plateforme));
		enregistrer(new DiskCollector(plateforme, config));
		natifs.forEach(this::enregistrer);

		registre.gauge("build_info",
						"Toujours 1. La version se lit dans les étiquettes — c'est l'idiome Prometheus "
								+ "pour publier une chaîne, qu'il ne sait pas stocker autrement.",
						"version", "platform", "server_version", "server")
				.set(1, Version.VALEUR, plateforme.type(), plateforme.versionServeur(),
						plateforme.nomServeur());
		debout.set(1);

		http = new MetricsHttpServer(plateforme, config, this::scraper);
		http.demarrer();

		// EN DERNIER, une fois tout en place : à partir d'ici les modules peuvent s'enregistrer,
		// et ils ne doivent pas trouver un noyau à moitié démarré.
		VaniaMetricsProvider.definir(this);
	}

	public void arreter() {
		VaniaMetricsProvider.definir(null);
		if (http != null) {
			http.arreter();
		}
		actifs.keySet().forEach(this::retirer);
	}

	// ---------------------------------------------------------------- relevés

	/**
	 * Un relevé, chronométré et protégé.
	 *
	 * <p>L'EXCEPTION NE REMONTE PAS. Un collecteur qui échoue est compté et oublié : une base
	 * injoignable ne doit pas faire disparaître le TPS de Grafana, qui est précisément ce qu'on
	 * regarde quand quelque chose ne va pas.
	 */
	private void relever(Collector c) {
		long debut = System.nanoTime();
		try {
			if (c.filPrincipal()) {
				plateforme.surFilPrincipal(() -> {
					try {
						c.relever(registre);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			} else {
				c.relever(registre);
			}
			AtomicLong t = dernierReleve.get(c.nom());
			if (t != null) {
				t.set(System.currentTimeMillis());
			}
		} catch (java.util.concurrent.TimeoutException e) {
			// LE FIL PRINCIPAL N'A PAS RÉPONDU EN CINQ SECONDES. C'est attendu au DÉMARRAGE, où
			// il charge les mondes et les plugins : le premier scrape tombe dessus, les suivants
			// passent. Constaté une fois, jamais rediffusé.
			//
			// Compté comme les autres erreurs — mc_exporter_scrape_errors_total ne ment pas —
			// mais sans pile d'appel : cinquante lignes de trace pour un état qui se répare seul
			// noient les vraies pannes dans les journaux.
			erreurs.inc(c.nom());
			plateforme.avertir("collecteur " + c.nom() + " — le fil principal n'a pas répondu en "
					+ "5 s ; normal si le serveur démarre encore");
		} catch (Throwable e) {
			// Throwable : un module dont le plugin visé a changé de version échoue en
			// NoSuchMethodError, qui est une Error. Le compter vaut mieux que tout perdre.
			erreurs.inc(c.nom());
			plateforme.erreur("collecteur " + c.nom(), e);
		} finally {
			dureeCollecteur.observe((System.nanoTime() - debut) / 1e9, c.nom());
		}
	}

	/** Appelé par le fil HTTP. Doit rendre la main vite : voir {@link Collector}. */
	private String scraper() {
		long debut = System.nanoTime();
		for (Collector c : auScrape) {
			relever(c);
		}
		long maintenant = System.currentTimeMillis();
		dernierReleve.forEach((nom, t) -> {
			long quand = t.get();
			ageFond.set(quand == 0 ? Double.NaN : (maintenant - quand) / 1000.0, nom);
		});
		String texte = registre.rendre();
		dureeScrape.observe((System.nanoTime() - debut) / 1e9);
		return texte;
	}

	private void declarerSesPropresMetriques() {
		dureeScrape = registre.histogram("exporter_scrape_duration_seconds",
				"Temps passé à répondre à un scrape. Si ça monte, c'est l'exportateur le problème.",
				new double[] {0.001, 0.005, 0.010, 0.025, 0.050, 0.100, 0.250, 1.0});
		dureeCollecteur = registre.histogram("exporter_collector_duration_seconds",
				"Temps d'un relevé, collecteur par collecteur.",
				new double[] {0.001, 0.005, 0.010, 0.050, 0.100, 0.500, 1.0, 5.0}, "collector");
		erreurs = registre.counter("exporter_scrape_errors_total",
				"Relevés qui ont levé une exception. Un collecteur en panne n'arrête pas les autres.",
				"collector");
		ageFond = registre.gauge("exporter_background_task_age_seconds",
				"Âge du dernier relevé d'un collecteur de fond. C'est LA métrique qui distingue "
						+ "« rien ne bouge » de « plus personne ne mesure ».",
				"collector");
		debout = registre.gauge("exporter_up", "1 quand l'exportateur a fini de démarrer.");
		inventaire = registre.gauge("exporter_collector_info",
				"Toujours 1, un par collecteur en service. « source » dit d'où viennent ses "
						+ "chiffres : « core » pour ce que le serveur expose lui-même, le nom du "
						+ "plugin sinon. C'est la réponse interrogeable à « d'où vient cette "
						+ "métrique ».",
				"collector", "source", "mode");
	}
}
