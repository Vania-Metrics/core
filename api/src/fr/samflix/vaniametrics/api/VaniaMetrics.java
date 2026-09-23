package fr.samflix.vaniametrics.api;

/**
 * LE CONTRAT ENTRE LE NOYAU ET LES MODULES.
 *
 * <p>Le noyau est un plugin ; chaque intégration en est un autre, dans son propre jar. Ils ne se
 * connaissent que par cette interface, qui vit dans l'artefact {@code vania-metrics-api} — le seul
 * que quelqu'un d'extérieur ait besoin de compiler.
 *
 * <p>UN MODULE FAIT TROIS CHOSES, et rien d'autre :
 *
 * <pre>{@code
 * // à l'activation
 * VaniaMetrics metriques = VaniaMetricsProvider.get();
 * collecteur = new MonCollecteur(metriques.plateforme());
 * metriques.enregistrer(collecteur);
 *
 * // à la désactivation
 * metriques.retirer(collecteur);
 * }</pre>
 *
 * <p>IL N'Y A PAS D'ORDRE À RESPECTER À L'ENREGISTREMENT. Un collecteur ajouté après le démarrage
 * du serveur HTTP est déclaré et programmé sur-le-champ ; il apparaît au scrape suivant. C'est ce
 * qui permet à un module d'être rechargé à chaud sans toucher au noyau.
 */
public interface VaniaMetrics {

	/** Le registre où déclarer ses instruments. */
	MetricRegistry registre();

	/** La plateforme d'accueil : journaux, ordonnanceur, recherche de services. */
	Platform plateforme();

	/**
	 * La configuration du noyau.
	 *
	 * <p>Un module y lit ses propres clés — par convention {@code module.<nom>.<clé>} — plutôt que
	 * d'ouvrir un fichier à lui. Un seul fichier à connaître pour l'opérateur, et l'environnement
	 * le surcharge de la même façon.
	 */
	Config config();

	/** La version du noyau, pour un module qui voudrait s'en assurer. */
	String version();

	/**
	 * Met un collecteur en service.
	 *
	 * <p>Le collecteur est déclaré puis, selon ce qu'il annonce, appelé à chaque scrape ou
	 * programmé en tâche de fond. Le rappeler avec le même collecteur ne fait rien.
	 */
	void enregistrer(Collector collecteur);

	/**
	 * Retire un collecteur.
	 *
	 * <p>À APPELER À LA DÉSACTIVATION DU MODULE, sans quoi le noyau continuerait d'interroger un
	 * collecteur dont les classes viennent d'un plugin déchargé — et le scrape tomberait en
	 * {@code NoClassDefFoundError} à chaque passage. Les séries déjà publiées disparaissent.
	 */
	void retirer(Collector collecteur);
}
