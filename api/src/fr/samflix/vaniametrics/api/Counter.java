package fr.samflix.vaniametrics.api;

/**
 * Une valeur qui ne fait qu'augmenter : morts, connexions, octets reçus.
 *
 * <p>ELLE NE REDESCEND JAMAIS, et c'est le contrat. Prometheus interprète toute baisse comme un
 * redémarrage du processus et recale son calcul de taux ; une remise à zéro « pour faire propre »
 * fabrique donc un pic qui n'a jamais existé. Le nom se termine par {@code _total}, convention que
 * Grafana et les règles d'alerte attendent.
 *
 * <p>Ce qu'on lit dans Grafana n'est presque jamais la valeur brute mais {@code rate(x[5m])} —
 * « combien par seconde ». D'où l'intérêt d'un compteur plutôt que d'une jauge tenue à la main :
 * le taux reste juste même si un scrape est perdu.
 */
public final class Counter extends Metric {

	Counter(String name, String help, String... labelNames) {
		super(name, help, labelNames);
	}

	@Override
	String type() {
		return "counter";
	}

	/** Un de plus. */
	public void inc(String... etiquettes) {
		add(1, etiquettes);
	}

	/**
	 * Recopie une valeur CUMULATIVE venue d'ailleurs.
	 *
	 * <p>Pour tout compteur qu'un autre tient déjà depuis le démarrage : {@code cpu.stat} du noyau,
	 * {@code getCollectionCount()} de la JVM, {@code Statistic.PLAYER_KILLS} du monde. On pose la
	 * valeur absolue au lieu de calculer un delta, ce qui évite de tenir un état et reste juste
	 * même si un relevé est sauté.
	 *
	 * <p>Ne JAMAIS confondre avec {@code clear()} suivi de {@code add()} : sur un compteur à
	 * étiquettes, le {@code clear()} effacerait les séries des autres étiquettes.
	 */
	public void mirror(double valeur, String... etiquettes) {
		if (Double.isNaN(valeur)) {
			return;
		}
		double[] s = serie(1, etiquettes);
		synchronized (s) {
			s[0] = valeur;
		}
	}

	/** {@code delta} de plus. Une valeur négative est ignorée plutôt que de casser le contrat. */
	public void add(double delta, String... etiquettes) {
		if (delta < 0) {
			return;
		}
		double[] s = serie(1, etiquettes);
		synchronized (s) {
			s[0] += delta;
		}
	}
}
