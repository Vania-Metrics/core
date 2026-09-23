package fr.samflix.vaniametrics.api;

import java.util.Optional;

/**
 * Le point d'accès au noyau depuis un module.
 *
 * <p>Sur Paper, le noyau s'annonce AUSSI dans le {@code ServicesManager} de Bukkit, qui est la voie
 * idiomatique ; ce fournisseur statique existe parce que Velocity n'a pas d'équivalent, et qu'un
 * module écrit pour les deux plateformes ne doit pas avoir à le savoir.
 *
 * <p>APPELER {@link #get()} DEPUIS UN MODULE EST SÛR À CONDITION QUE LE MODULE DÉPENDE DU NOYAU —
 * {@code depend: [VaniaMetrics]} dans son plugin.yml, {@code dependencies} dans son
 * velocity-plugin.json. Bukkit et Velocity garantissent alors que le noyau est activé avant lui.
 * Sans cette déclaration, l'ordre n'est garanti par rien.
 */
public final class VaniaMetricsProvider {

	private static volatile VaniaMetrics instance;

	private VaniaMetricsProvider() {}

	/** Le noyau, ou une exception s'il n'est pas encore là. */
	public static VaniaMetrics get() {
		VaniaMetrics v = instance;
		if (v == null) {
			throw new IllegalStateException(
					"VaniaMetrics n'est pas chargé. Le module dépend-il bien du noyau ?");
		}
		return v;
	}

	/** Le noyau, ou rien. Pour un code qui veut se taire au lieu d'échouer. */
	public static Optional<VaniaMetrics> chercher() {
		return Optional.ofNullable(instance);
	}

	/**
	 * RÉSERVÉ AU NOYAU. Un module qui appelle ceci casse tous les autres.
	 *
	 * <p>Publique faute de mieux : le noyau est dans un autre paquet et un autre jar, la visibilité
	 * de paquet ne peut donc pas l'atteindre. C'est le même compromis que LuckPerms et spark, pour
	 * la même raison.
	 */
	public static void definir(VaniaMetrics v) {
		instance = v;
	}
}
