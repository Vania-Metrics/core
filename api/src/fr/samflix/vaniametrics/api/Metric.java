package fr.samflix.vaniametrics.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La base commune aux trois instruments.
 *
 * <p>UNE FAMILLE, PLUSIEURS SÉRIES. Un instrument porte un nom, une aide, et une carte des séries
 * indexée par les VALEURS de ses étiquettes. {@code mc_world_entities} est une famille ;
 * {@code mc_world_entities{world="lobby"}} en est une série.
 *
 * <p>POURQUOI UNE ConcurrentHashMap ET DES DOUBLE ATOMIQUES. Les compteurs sont alimentés depuis
 * les fils d'événements du serveur — le fil principal, le fil réseau de PacketEvents, les tâches
 * asynchrones — et lus par le fil HTTP du scrape. Sans cela, on lirait des valeurs à moitié
 * écrites, et le plugin censé mesurer la santé du serveur y ajouterait une course.
 */
public abstract class Metric {

	/** Le nom complet, préfixe compris : {@code mc_tps}. */
	public final String name;
	/** La ligne {@code # HELP}. Obligatoire : un graphique sans légende ne se relit pas. */
	public final String help;
	/** Les NOMS des étiquettes, dans l'ordre. Les valeurs arrivent à l'écriture. */
	public final String[] labelNames;

	final Map<LabelValues, double[]> series = new ConcurrentHashMap<>();

	Metric(String name, String help, String... labelNames) {
		this.name = name;
		this.help = help;
		this.labelNames = labelNames;
	}

	/** Le mot qui va dans la ligne {@code # TYPE}. */
	abstract String type();

	/**
	 * La série correspondant à ces valeurs d'étiquettes, créée au besoin.
	 *
	 * <p>Le tableau rendu est l'état interne de la série : sa longueur dépend de l'instrument —
	 * 1 pour une jauge ou un compteur, {@code buckets + 2} pour un histogramme.
	 */
	double[] serie(int taille, String... valeurs) {
		if (valeurs.length != labelNames.length) {
			throw new IllegalArgumentException(
					name + " attend " + labelNames.length + " étiquette(s), pas " + valeurs.length);
		}
		return series.computeIfAbsent(new LabelValues(valeurs), k -> new double[taille]);
	}

	/**
	 * Oublie toutes les séries.
	 *
	 * <p>INDISPENSABLE AUX JAUGES À ÉTIQUETTES VARIABLES. Un monde déchargé, un type d'entité qui
	 * disparaît, une version de client qui n'est plus connectée : sans remise à zéro, leur dernière
	 * valeur resterait publiée pour toujours et Grafana montrerait des zombies. Les compteurs, eux,
	 * ne doivent JAMAIS être remis à zéro — Prometheus lirait un redémarrage.
	 */
	public void clear() {
		series.clear();
	}

	/** Une clé de série : les valeurs d'étiquettes, comparables et hachables. */
	static final class LabelValues {
		final String[] valeurs;
		private final int hash;

		LabelValues(String[] valeurs) {
			this.valeurs = valeurs;
			this.hash = java.util.Arrays.hashCode(valeurs);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof LabelValues autre && java.util.Arrays.equals(valeurs, autre.valeurs);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}
}
