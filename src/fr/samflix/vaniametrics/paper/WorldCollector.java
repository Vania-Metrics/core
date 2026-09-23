package fr.samflix.vaniametrics.paper;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Les mondes : entités, blocs-entités, chunks.
 *
 * <p>TROIS COMPTEURS EN O(1), ET C'EST LA RAISON D'ÊTRE DE CE COLLECTEUR. Paper expose
 * {@code getEntityCount()}, {@code getTileEntityCount()} et {@code getChunkCount()} : des
 * compteurs déjà tenus par le serveur, lisibles sans rien parcourir. La plupart des exportateurs
 * font {@code getEntities().size()}, qui CONSTRUIT une liste de toutes les entités du monde à
 * chaque scrape — sur un serveur chargé, l'exportateur devient alors lui-même une source de lag.
 *
 * <p>LE DÉTAIL PAR TYPE, LUI, EST FORCÉMENT EN O(n) — il faut bien regarder chaque entité. Il est
 * donc en fond et non au scrape, et il est BORNÉ : seuls les types qui dépassent un seuil sont
 * publiés. Sans cela, cent types d'entités sur trois mondes feraient trois cents séries
 * temporelles pour compter des valeurs à zéro.
 */
final class WorldCollector implements Collector {

	private final int seuilType;

	private Gauge entites;
	private Gauge blocsEntites;
	private Gauge chunks;
	private Gauge joueurs;
	private Gauge parType;
	private Gauge tempsMonde;
	private Gauge orage;

	WorldCollector(Config config) {
		this.seuilType = config.entier("collector.world.entity_type_threshold", 5);
	}

	@Override
	public String nom() {
		return "world";
	}

	@Override
	public boolean filPrincipal() {
		return true;
	}

	@Override
	public boolean enFond() {
		// En fond à cause du seul détail par type. Le reste tiendrait au scrape, mais séparer
		// ferait deux collecteurs pour un même sujet — et dix secondes de retard sur un nombre
		// d'entités n'a jamais changé un diagnostic.
		return true;
	}

	@Override
	public long intervalleSecondes() {
		return 15;
	}

	@Override
	public void declarer(MetricRegistry r) {
		entites = r.gauge("world_entities", "Entités chargées. Compteur O(1) du serveur.", "world");
		blocsEntites = r.gauge("world_tile_entities",
				"Blocs-entités chargés : coffres, fours, hoppers. Les hoppers sont la première "
						+ "cause de tick lourd sur un serveur de construction.",
				"world");
		chunks = r.gauge("world_chunks_loaded", "Chunks chargés.", "world");
		joueurs = r.gauge("world_players", "Joueurs présents dans le monde.", "world");
		parType = r.gauge("world_entities_by_type",
				"Entités par type. Relevé en fond, et seuls les types au-dessus du seuil sont "
						+ "publiés — sinon la cardinalité explose pour compter des zéros.",
				"world", "entity_type");
		tempsMonde = r.gauge("world_time_ticks", "Heure du monde, en ticks.", "world");
		orage = r.gauge("world_weather",
				"Météo. 0 = clair, 1 = pluie, 2 = orage.", "world");
	}

	@Override
	public void relever(MetricRegistry r) {
		// Les mondes vont et viennent — Multiverse en crée et en décharge. Sans remise à zéro,
		// un monde déchargé garderait sa dernière valeur publiée pour toujours.
		parType.clear();

		for (World monde : Bukkit.getWorlds()) {
			String nom = monde.getName();
			entites.set(monde.getEntityCount(), nom);
			blocsEntites.set(monde.getTileEntityCount(), nom);
			chunks.set(monde.getChunkCount(), nom);
			joueurs.set(monde.getPlayers().size(), nom);
			tempsMonde.set(monde.getFullTime(), nom);
			orage.set(monde.isThundering() ? 2 : monde.hasStorm() ? 1 : 0, nom);

			if (seuilType < 0) {
				continue;
			}
			Map<String, Integer> compte = new HashMap<>();
			for (Entity e : monde.getEntities()) {
				compte.merge(e.getType().name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
			}
			compte.forEach((type, n) -> {
				if (n >= seuilType) {
					parType.set(n, nom, type);
				}
			});
		}
	}
}
