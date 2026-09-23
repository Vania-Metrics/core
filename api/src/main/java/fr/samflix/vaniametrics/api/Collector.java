package fr.samflix.vaniametrics.api;

/**
 * Une source de métriques.
 *
 * <p>DEUX RÉGIMES, ET LES CONFONDRE FABRIQUE LE LAG QU'ON PRÉTEND MESURER :
 *
 * <ul>
 *   <li><b>au scrape</b> ({@link #enFond()} faux) — appelé par le fil HTTP au moment où Prometheus
 *       interroge. Réservé à ce qui coûte quelques microsecondes : lire un compteur de la JVM,
 *       un fichier de cgroup, {@code World.getEntityCount()}. Une quinzaine de millisecondes ici
 *       et c'est un tick perdu toutes les quinze secondes.
 *   <li><b>en fond</b> ({@link #enFond()} vrai) — appelé par une tâche périodique, qui dépose le
 *       résultat dans les instruments. Le scrape rend alors la dernière valeur connue. C'est le
 *       régime de tout ce qui parcourt une liste, interroge une base ou lit un disque.
 * </ul>
 *
 * <p>UN COLLECTEUR QUI ÉCHOUE NE DOIT PAS EMPORTER LE SCRAPE. {@link Exporter} attrape ses
 * exceptions, les compte dans {@code mc_exporter_scrape_errors_total} et passe au suivant : une
 * base injoignable ne doit pas faire disparaître le TPS.
 */
public interface Collector {

	/** Un nom court, qui sert d'étiquette dans les métriques de l'exportateur et de clé de config. */
	String nom();

	/**
	 * D'où viennent ces chiffres.
	 *
	 * <p>« core » pour ce que le serveur ou la machine exposent d'eux-mêmes ; le NOM DU PLUGIN
	 * sinon — « LuckPerms », « BetonQuest », « spark ». Publié dans
	 * {@code mc_exporter_collector_info}, ce qui rend l'origine d'une métrique interrogeable en
	 * PromQL au lieu de se lire dans un document qui finira par mentir :
	 *
	 * <pre>{@code mc_exporter_collector_info{collector="quest"}  ->  source="BetonQuest"}</pre>
	 */
	default String origine() {
		return "core";
	}

	/** Déclare les instruments. Appelé une fois, au démarrage. */
	default void declarer(MetricRegistry r) {}

	/** Relève les valeurs. Appelé au scrape, ou périodiquement si {@link #enFond()}. */
	void relever(MetricRegistry r) throws Exception;

	/** Voir la note de classe. Faux par défaut : on ne passe en fond que sur preuve de coût. */
	default boolean enFond() {
		return false;
	}

	/** L'intervalle, en secondes, pour un collecteur de fond. Ignoré sinon. */
	default long intervalleSecondes() {
		return 30;
	}

	/**
	 * Le relevé doit-il courir sur le fil principal du serveur ?
	 *
	 * <p>Vrai pour tout ce qui touche à l'API Bukkit — la liste des mondes, les entités, les
	 * joueurs. Faux pour la JVM, les cgroups, le disque et le SQL, qui n'ont rien à y faire et
	 * voleraient du temps de tick pour rien.
	 */
	default boolean filPrincipal() {
		return false;
	}

	/** Libère ce qui doit l'être à l'arrêt : connexions, écouteurs. */
	default void fermer() {}
}
