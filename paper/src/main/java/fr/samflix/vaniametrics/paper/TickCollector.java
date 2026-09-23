package fr.samflix.vaniametrics.paper;

import org.bukkit.Bukkit;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * La boucle de jeu, vue par Paper seul — sans spark, qui a son module.
 *
 * <p>LE TPS NE SUFFIT PAS, ET LE MSPT EST LA VRAIE MÉTRIQUE. Le TPS plafonne à 20 : un serveur qui
 * met 45 ms par tick affiche 20,0 tant qu'il rattrape son retard, et n'affiche 19 qu'une fois
 * franchie la barre des 50 ms — c'est-à-dire trop tard. La durée de tick, elle, monte
 * proportionnellement à la charge dès la première milliseconde.
 *
 * <p>{@code getTickTimes()} rend les durées BRUTES en nanosecondes, une par tick sur la dernière
 * minute. C'est mieux que des quantiles pré-calculés : on en fait un histogramme, et Grafana
 * calcule ensuite n'importe quel quantile sur n'importe quelle fenêtre.
 *
 * <p>CHAQUE TICK N'EST COMPTÉ QU'UNE FOIS. Le tampon de Paper est glissant et se recouvre d'un
 * scrape à l'autre : sans mémoire du dernier tick vu, on observerait les mêmes durées plusieurs
 * fois et l'histogramme mentirait. D'où le suivi de {@code getCurrentTick()}.
 */
final class TickCollector implements Collector {

	private Gauge tps;
	private Gauge dureeMoyenne;
	private Histogram duree;
	private Counter ticks;
	private Gauge joueursMax;

	private int dernierTickVu = -1;

	@Override
	public String nom() {
		return "tick";
	}

	@Override
	public boolean filPrincipal() {
		// getTickTimes() lit un tampon que le fil du serveur écrit. Le lire ailleurs donnerait
		// des valeurs à moitié écrites — rarement, et donc au pire moment.
		return true;
	}

	@Override
	public void declarer(MetricRegistry r) {
		tps = r.gauge("server_tps",
				"Ticks par seconde, plafonné à 20. window = 1m|5m|15m. À ne PAS utiliser comme "
						+ "signal d'alerte : il ne bouge qu'une fois le serveur déjà en retard.",
				"window");
		dureeMoyenne = r.gauge("server_tick_average_seconds",
				"Durée moyenne d'un tick, telle que Paper la calcule.");
		duree = r.histogram("server_tick_duration_seconds",
				"Distribution des durées de tick. 0,05 s est le seuil : au-delà, le serveur prend "
						+ "du retard. C'est LE signal d'alerte.",
				Histogram.SECONDES_TICK);
		ticks = r.counter("server_ticks_total", "Ticks écoulés depuis le démarrage.");
		joueursMax = r.gauge("server_players_max", "Places annoncées par le serveur.");
	}

	@Override
	public void relever(MetricRegistry r) {
		double[] valeurs = Bukkit.getTPS();
		String[] fenetres = {"1m", "5m", "15m"};
		for (int i = 0; i < valeurs.length && i < fenetres.length; i++) {
			tps.set(Math.min(valeurs[i], 20.0), fenetres[i]);
		}

		dureeMoyenne.set(Bukkit.getAverageTickTime() / 1000.0);

		int tickCourant = Bukkit.getCurrentTick();
		ticks.mirror(tickCourant);

		long[] durees = Bukkit.getTickTimes();
		if (durees != null && durees.length > 0) {
			// Le tampon contient les N derniers ticks, le plus récent en dernier. On ne reprend
			// que ceux écoulés depuis le relevé précédent — au plus la taille du tampon.
			int nouveaux = dernierTickVu < 0 ? durees.length
					: Math.min(tickCourant - dernierTickVu, durees.length);
			for (int i = durees.length - nouveaux; i < durees.length; i++) {
				if (i >= 0 && durees[i] > 0) {
					duree.observe(durees[i] / 1e9);
				}
			}
		}
		dernierTickVu = tickCourant;

		joueursMax.set(Bukkit.getMaxPlayers());
	}
}
