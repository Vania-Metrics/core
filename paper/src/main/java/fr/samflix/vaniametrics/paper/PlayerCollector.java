package fr.samflix.vaniametrics.paper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Joueur;
import fr.samflix.vaniametrics.api.PlayerSeries;

/**
 * Les joueurs connectés — EN AGRÉGAT, jamais nommément.
 *
 * <p>DEUX NIVEAUX, ET IL FAUT LES DISTINGUER.
 *
 * <p>Les <b>agrégats</b> d'abord — sommes, histogrammes, répartitions sur des ensembles bornés :
 * une marque de client, une langue. Ils n'ont aucune étiquette de joueur et répondent à « combien,
 * quand ».
 *
 * <p>Le <b>détail par joueur</b> ensuite, et seulement pour les joueurs CONNECTÉS, sous un plafond
 * — voir {@link PlayerSeries}, qui porte tout le raisonnement. Il répond à « qui », ce qu'un
 * agrégat ne saura jamais faire, et il le fait sans base de données ni collecteur de logs.
 *
 * <p>Les statistiques vanilla sont cumulatives depuis toujours et par joueur ; on les SOMME sur
 * les joueurs connectés. Ce total redescend donc quand un joueur se déconnecte, ce qui en fait une
 * jauge et non un compteur — le compteur, lui, est alimenté par les événements.
 */
final class PlayerCollector implements Collector {

	private Gauge enLigne;
	private Histogram ping;
	private Gauge parMarque;
	private Gauge parLangue;
	private Gauge statistiques;
	private Counter tempsJeu;

	// Le détail par joueur. Les instruments sont passés à PlayerSeries, qui les efface quand la
	// population change — sans quoi un joueur parti garderait sa dernière valeur pour toujours.
	private Gauge joueurPing;
	private Gauge joueurTempsJeu;
	private Gauge joueurStatistique;
	private PlayerSeries series;

	private final Config config;

	PlayerCollector(Config config) {
		this.config = config;
	}

	@Override
	public String nom() {
		return "players";
	}

	@Override
	public boolean filPrincipal() {
		return true;
	}

	@Override
	public void declarer(MetricRegistry r) {
		enLigne = r.gauge("server_players_online", "Joueurs connectés.");
		ping = r.histogram("server_players_ping_seconds",
				"Distribution du ping des joueurs connectés.", Histogram.SECONDES_PING);
		// LA VERSION DU CLIENT N'EST PAS ICI, et c'est vérifié : org.bukkit.entity.Player n'a
		// aucune méthode qui la donne. Elle passe par ViaVersion, donc par un MODULE — c'est
		// exactement la frontière que le système de modules trace : le noyau ne connaît que ce
		// que la plateforme expose d'elle-même.
		parMarque = r.gauge("server_players_by_brand",
				"Joueurs par marque de client annoncée. Falsifiable en un mod : elle renseigne "
						+ "sur les joueurs honnêtes, pas sur les tricheurs.",
				"brand");
		parLangue = r.gauge("server_players_by_locale", "Joueurs par langue du client.", "locale");
		statistiques = r.gauge("server_player_statistics",
				"Somme d'une statistique vanilla sur les joueurs CONNECTÉS. Redescend quand ils "
						+ "partent : c'est une jauge, pas un compteur.",
				"statistic");
		tempsJeu = r.counter("server_playtime_seconds_total",
				"Temps de jeu cumulé, tous joueurs connectés confondus.");

		// « player » ET « uuid » sur chacune : le pseudonyme pour lire et filtrer, l'identifiant
		// pour que la série survive à un changement de pseudonyme. Voir Joueur.
		joueurPing = r.gauge("server_player_ping_seconds",
				"Ping d'un joueur connecté.", "player", "uuid");
		joueurTempsJeu = r.gauge("server_player_playtime_seconds",
				"Temps de jeu total d'un joueur connecté.", "player", "uuid");
		joueurStatistique = r.gauge("server_player_statistic",
				"Une statistique vanilla, joueur par joueur. Publiée tant qu'il est connecté — "
						+ "ce qui suffit, puisqu'il ne peut rien faire hors ligne.",
				"player", "uuid", "statistic");
		series = new PlayerSeries(r, config);
	}

	@Override
	public void relever(MetricRegistry r) {
		var joueurs = Bukkit.getOnlinePlayers();
		enLigne.set(joueurs.size());

		var connectes = joueurs.stream().map(PlayerCollector::identite).toList();
		var retenus = series.retenir(connectes, joueurPing, joueurTempsJeu, joueurStatistique);
		// Les identifiants retenus, pour savoir à qui publier le détail sans refaire le plafond.
		var publies = retenus.stream().map(Joueur::uuid).collect(java.util.stream.Collectors.toSet());

		// Les répartitions sont remises à zéro : une version de client qui n'est plus connectée
		// doit disparaître, pas rester figée sur sa dernière valeur.
		parMarque.clear();
		parLangue.clear();

		Map<String, Integer> marques = new HashMap<>();
		Map<String, Integer> langues = new HashMap<>();
		long kills = 0;
		long mobs = 0;
		long morts = 0;
		long degatsInfliges = 0;
		long degatsSubis = 0;
		long sauts = 0;
		long minutes = 0;

		for (Player j : joueurs) {
			ping.observe(j.getPing() / 1000.0);
			if (publies.contains(j.getUniqueId().toString())) {
				detail(j);
			}
			marques.merge(marque(j), 1, Integer::sum);
			langues.merge(langue(j), 1, Integer::sum);

			kills += lire(j, Statistic.PLAYER_KILLS);
			mobs += lire(j, Statistic.MOB_KILLS);
			morts += lire(j, Statistic.DEATHS);
			degatsInfliges += lire(j, Statistic.DAMAGE_DEALT);
			degatsSubis += lire(j, Statistic.DAMAGE_TAKEN);
			sauts += lire(j, Statistic.JUMP);
			minutes += lire(j, Statistic.PLAY_ONE_MINUTE);
		}

		marques.forEach((m, n) -> parMarque.set(n, m));
		langues.forEach((l, n) -> parLangue.set(n, l));

		statistiques.set(kills, "player_kills");
		statistiques.set(mobs, "mob_kills");
		statistiques.set(morts, "deaths");
		// Les dégâts vanilla sont en dixièmes de cœur ; on rend des points de vie, qui sont
		// l'unité que le joueur voit.
		statistiques.set(degatsInfliges / 10.0, "damage_dealt");
		statistiques.set(degatsSubis / 10.0, "damage_taken");
		statistiques.set(sauts, "jumps");
		// PLAY_ONE_MINUTE compte des TICKS malgré son nom — un piège classique de l'API.
		tempsJeu.mirror(minutes / 20.0);
	}

	/** Le détail d'un joueur. Appelé seulement pour ceux que le plafond laisse passer. */
	private void detail(Player j) {
		Joueur qui = identite(j);
		joueurPing.set(j.getPing() / 1000.0, qui.etiquettes());
		joueurTempsJeu.set(lire(j, Statistic.PLAY_ONE_MINUTE) / 20.0, qui.etiquettes());
		joueurStatistique.set(lire(j, Statistic.PLAYER_KILLS), qui.etiquettes("player_kills"));
		joueurStatistique.set(lire(j, Statistic.MOB_KILLS), qui.etiquettes("mob_kills"));
		joueurStatistique.set(lire(j, Statistic.DEATHS), qui.etiquettes("deaths"));
		joueurStatistique.set(lire(j, Statistic.DAMAGE_DEALT) / 10.0, qui.etiquettes("damage_dealt"));
		joueurStatistique.set(lire(j, Statistic.DAMAGE_TAKEN) / 10.0, qui.etiquettes("damage_taken"));
	}

	/** Le couple pseudonyme / identifiant, dans la forme que {@link Joueur#de} impose à tous. */
	private static Joueur identite(Player j) {
		return Joueur.de(j.getUniqueId(), j.getName());
	}

	private int lire(Player j, Statistic s) {
		try {
			return j.getStatistic(s);
		} catch (IllegalArgumentException e) {
			// Une statistique retirée par une version de Minecraft. Zéro plutôt qu'une panne.
			return 0;
		}
	}

	private String marque(Player j) {
		String m = j.getClientBrandName();
		return m == null || m.isBlank() ? "unknown" : m.toLowerCase(Locale.ROOT);
	}

	private String langue(Player j) {
		// locale() et non getLocale(), qui est déprécié : le premier rend un java.util.Locale
		// déjà normalisé, le second une chaîne brute du client.
		java.util.Locale l = j.locale();
		return l == null ? "unknown" : l.toLanguageTag().toLowerCase(Locale.ROOT);
	}
}
