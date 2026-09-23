package fr.samflix.vaniametrics.api;

/**
 * Une valeur qui monte et descend : joueurs connectés, mémoire occupée, TPS.
 *
 * <p>C'est l'instrument par défaut, et celui qu'on choisit quand on hésite. La question qui tranche
 * est : « est-ce que la valeur peut descendre ? » Si oui, jauge. Si elle ne fait qu'augmenter
 * jusqu'au redémarrage, c'est un {@link Counter}, et le distinguer n'est pas cosmétique : Grafana
 * applique {@code rate()} aux compteurs et jamais aux jauges.
 */
public final class Gauge extends Metric {

	Gauge(String name, String help, String... labelNames) {
		super(name, help, labelNames);
	}

	@Override
	String type() {
		return "gauge";
	}

	/** Pose la valeur de la série désignée par ces étiquettes. */
	public void set(double valeur, String... etiquettes) {
		serie(1, etiquettes)[0] = valeur;
	}

	/** Ajoute à la série désignée. Utile pour agréger monde par monde. */
	public void add(double delta, String... etiquettes) {
		double[] s = serie(1, etiquettes);
		synchronized (s) {
			s[0] += delta;
		}
	}
}
