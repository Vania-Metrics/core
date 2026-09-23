package fr.samflix.vaniametrics.api;

import java.nio.file.Path;

/**
 * Ce que le noyau attend de sa plateforme d'accueil.
 *
 * <p>C'EST TOUTE LA FRONTIÈRE entre le noyau et les adaptateurs. Le noyau ne connaît ni Bukkit ni
 * Velocity ; il connaît cette interface, et les deux adaptateurs la remplissent chacun à sa
 * manière. Elle est petite exprès : chaque méthode ajoutée ici est une chose de plus à implémenter
 * deux fois, et une chance de plus que les deux plateformes divergent.
 *
 * <p>ELLE EST DANS L'API et non dans le noyau parce qu'un module en a besoin : pour journaliser
 * dans le bon fichier, pour programmer une tâche, et pour chercher un service sans savoir si la
 * plateforme a un ServicesManager. Un module ne l'implémente jamais — il la reçoit.
 */
public interface Platform {

	/** « paper » ou « velocity ». Sert d'étiquette dans {@code mc_build_info}. */
	String type();

	/** Le nom de cette instance dans le réseau : « lobby », « proxy ». Étiquette {@code server}. */
	String nomServeur();

	/** La version du logiciel serveur, pour {@code mc_build_info}. */
	String versionServeur();

	/** Le répertoire de configuration du plugin. */
	Path repertoire();

	void info(String message);

	void avertir(String message);

	void erreur(String message, Throwable cause);

	/** Un plugin est-il présent ? Sert à n'activer un collecteur que s'il a de quoi travailler. */
	boolean pluginPresent(String nom);

	/**
	 * Cherche un service fourni par un autre plugin.
	 *
	 * <p>C'EST LA VOIE OFFICIELLE SUR PAPER, et elle n'a pas d'équivalent sur Velocity. Bukkit a
	 * un {@code ServicesManager} où LuckPerms, Vault, spark et EssentialsX déposent leur point
	 * d'entrée ; Velocity n'en a pas, et chaque plugin y expose un fournisseur statique.
	 *
	 * <p>Ce détour a été ajouté après une vraie surprise : {@code SparkProvider.get()} ne rend
	 * rien sur Paper, parce que spark y est intégré au serveur et s'annonce par le
	 * {@code ServicesManager}. Un module qui veut marcher des deux côtés doit donc essayer les
	 * deux voies, et c'est la plateforme qui sait laquelle existe chez elle.
	 */
	default <T> java.util.Optional<T> service(Class<T> type) {
		return java.util.Optional.empty();
	}

	/**
	 * Exécute sur le fil principal du serveur et ATTEND le résultat.
	 *
	 * <p>Sur Velocity il n'y a pas de fil principal : l'implémentation exécute sur place. Sur
	 * Paper, elle passe par l'ordonnanceur et bloque le fil appelant — qui est celui d'une tâche de
	 * fond, jamais celui du serveur.
	 */
	void surFilPrincipal(Runnable tache) throws Exception;

	/** Programme une tâche répétée, hors du fil principal. */
	void repeter(Runnable tache, long intervalleSecondes);
}
