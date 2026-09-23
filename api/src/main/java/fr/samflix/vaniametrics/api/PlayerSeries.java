package fr.samflix.vaniametrics.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * LE SEUL ENDROIT OÙ L'ON A LE DROIT D'ÉTIQUETER PAR JOUEUR.
 *
 * <p>Une étiquette {@code player="…"} crée une série temporelle par joueur. La règle qu'on se
 * donnait au départ — « jamais » — était juste à l'échelle de mille joueurs et fausse à la nôtre :
 * cinquante places et sept crânes font trois cent cinquante séries, quelques mégaoctets, à comparer
 * aux près de trois cents séries que le serveur publie déjà. Ce qui compte n'est donc pas le
 * principe mais LA BORNE, et cette classe est la borne.
 *
 * <p>DEUX GARANTIES, et elles se complètent :
 *
 * <ol>
 *   <li><b>seuls les joueurs CONNECTÉS sont publiés.</b> Ce n'est pas une perte : un joueur ne peut
 *       déclencher un événement que connecté — trouver un crâne, gagner de l'argent, mourir. Une
 *       série qui ne vit que pendant la session capture donc la totalité de ce qu'il fait. Quand il
 *       part, Prometheus pose un marqueur d'obsolescence et la série s'arrête proprement, son
 *       historique conservé ;
 *   <li><b>un plafond dur.</b> Au-delà, on cesse de publier le détail et {@code
 *       mc_exporter_player_series_dropped} le dit. Une dégradation annoncée vaut mieux qu'un
 *       Prometheus qui gonfle en silence.
 * </ol>
 *
 * <p>Ce qui reste interdit, et sans exception : étiqueter par joueur une métrique à haute
 * fréquence — un paquet, un bloc cassé. Ici on publie des ÉTATS, pas des flux.
 *
 * <p>DEUX ÉTIQUETTES PAR JOUEUR, {@code player} et {@code uuid} — voir {@link Joueur}. Elles ne
 * coûtent pas une série de plus, puisqu'elles se correspondent exactement, et elles évitent d'avoir
 * à choisir entre un tableau de bord lisible et un suivi qui survit à un changement de pseudonyme.
 */
public final class PlayerSeries {

	private final Config config;
	private final Gauge abandons;

	/**
	 * Les joueurs publiés au dernier passage, pour savoir quelles séries effacer.
	 *
	 * <p>Indexés par IDENTIFIANT et non par pseudonyme : un joueur qui se renomme en cours de
	 * session ne doit pas passer pour deux personnes différentes.
	 */
	private final Set<String> publies = new LinkedHashSet<>();

	public PlayerSeries(MetricRegistry registre, Config config) {
		this.config = config;
		this.abandons = registre.gauge("exporter_player_series_dropped",
				"Joueurs dont le détail n'est PAS publié, faute de place sous le plafond "
						+ "collector.players.max_series. Une valeur non nulle veut dire que les "
						+ "métriques par joueur sont incomplètes — et le dire vaut mieux que de "
						+ "laisser croire le contraire.");
	}

	/** Le détail par joueur est-il demandé ? */
	public boolean actif() {
		return config.actif("collector.players.per_player", true);
	}

	/**
	 * Décide qui publier, et efface les séries de ceux qui ne le sont plus.
	 *
	 * <p>À appeler AU DÉBUT de chaque relevé, avec les joueurs connectés. Les instruments passés
	 * sont remis à zéro pour les noms disparus — sans quoi un joueur déconnecté garderait sa
	 * dernière valeur publiée pour toujours.
	 *
	 * @return les joueurs à publier, plafond appliqué. Vide si le détail est désactivé.
	 */
	public Collection<Joueur> retenir(Collection<Joueur> connectes, Metric... instruments) {
		if (!actif()) {
			effacer(instruments);
			abandons.set(connectes.size());
			return List.of();
		}
		int plafond = config.entier("collector.players.max_series", 200);
		List<Joueur> retenus = new ArrayList<>();
		Set<String> identifiants = new LinkedHashSet<>();
		int ignores = 0;
		for (Joueur j : connectes) {
			if (retenus.size() < plafond) {
				retenus.add(j);
				identifiants.add(j.uuid());
			} else {
				ignores++;
			}
		}
		abandons.set(ignores);

		// On efface TOUT puis on republie : distinguer les séries à retirer une par une
		// demanderait de connaître les étiquettes de chaque instrument, que seul l'appelant
		// connaît. Effacer coûte une carte vidée, republier coûte ce qu'on allait écrire de
		// toute façon.
		if (!publies.equals(identifiants)) {
			effacer(instruments);
			publies.clear();
			publies.addAll(identifiants);
		}
		return retenus;
	}

	private void effacer(Metric... instruments) {
		for (Metric m : instruments) {
			if (m != null) {
				m.clear();
			}
		}
	}
}
