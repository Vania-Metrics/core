package fr.samflix.vaniametrics.api;

/**
 * Une distribution : durées de tick, ping des joueurs, longueur des sessions.
 *
 * <p>POURQUOI PAS UNE MOYENNE. Parce qu'une moyenne ment sur exactement ce qui compte. Un serveur
 * dont 99 % des ticks tiennent en 1 ms et 1 % en 400 ms affiche 5 ms de moyenne — un chiffre
 * rassurant qui décrit un serveur qui saccade quatre fois par seconde. L'histogramme garde la
 * forme de la distribution et laisse Grafana calculer n'importe quel quantile après coup, sur
 * n'importe quelle fenêtre, avec {@code histogram_quantile()}.
 *
 * <p>CE QUE PROMETHEUS ATTEND, et qui n'est pas négociable :
 * <ul>
 *   <li>des seaux <b>cumulatifs</b> — le seau {@code le="0.05"} compte tout ce qui est
 *       ≤ 50 ms, y compris ce qui est déjà dans {@code le="0.01"} ;
 *   <li>un seau {@code le="+Inf"} final, égal au compte total ;
 *   <li>les séries {@code _sum} et {@code _count} à côté.
 * </ul>
 *
 * <p>LE COÛT EST EN SÉRIES : un histogramme de 12 seaux publie 14 séries temporelles par
 * combinaison d'étiquettes. C'est pour cette raison qu'aucun histogramme de ce plugin n'est
 * étiqueté par joueur — voir la note sur la cardinalité dans docs/metriques.md.
 */
public final class Histogram extends Metric {

	/**
	 * Des secondes, de 1 ms à 2 s, taillées pour une boucle de jeu à 50 ms le tick.
	 *
	 * <p>Les seuils ne sont pas décoratifs : 0,05 est la durée d'un tick plein, et c'est LE seuil
	 * qui sépare « le serveur suit » de « le serveur prend du retard ». Les seaux au-dessus disent
	 * de combien il décroche.
	 */
	public static final double[] SECONDES_TICK = {
		0.001, 0.005, 0.010, 0.025, 0.050, 0.075, 0.100, 0.250, 0.500, 1.0, 2.0
	};

	/** Des secondes, de 5 ms à 2 s : le ping d'un joueur, du LAN à l'autre bout du monde. */
	public static final double[] SECONDES_PING = {
		0.005, 0.010, 0.025, 0.050, 0.100, 0.150, 0.200, 0.300, 0.500, 1.0, 2.0
	};

	/** Des secondes, d'une minute à six heures : la durée d'une session de jeu. */
	public static final double[] SECONDES_SESSION = {
		60, 300, 900, 1800, 3600, 7200, 14400, 21600
	};

	final double[] seuils;

	Histogram(String name, String help, double[] seuils, String... labelNames) {
		super(name, help, labelNames);
		this.seuils = seuils;
	}

	@Override
	String type() {
		return "histogram";
	}

	/**
	 * Range une observation.
	 *
	 * <p>Le tableau interne fait {@code seuils.length + 2} cases : un compteur par seuil, puis la
	 * somme, puis le compte total. Le seau {@code +Inf} n'est pas stocké — il vaut le compte.
	 */
	public void observe(double valeur, String... etiquettes) {
		double[] s = serie(seuils.length + 2, etiquettes);
		synchronized (s) {
			for (int i = 0; i < seuils.length; i++) {
				if (valeur <= seuils[i]) {
					s[i]++;
				}
			}
			s[seuils.length] += valeur;
			s[seuils.length + 1]++;
		}
	}
}
