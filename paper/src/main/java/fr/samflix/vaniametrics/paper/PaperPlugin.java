package fr.samflix.vaniametrics.paper;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.core.Exporter;

/**
 * Le point d'entrée Paper.
 *
 * <p>Il ne fait rien d'autre que construire la plateforme et démarrer l'exportateur : toute la
 * logique est dans le noyau, et les collecteurs propres à Paper sont à côté. C'est ce qui permet à
 * l'adaptateur Velocity d'être aussi court pour le même résultat.
 */
public final class PaperPlugin extends JavaPlugin {

	private Exporter exportateur;
	private GameEventListener evenements;

	@Override
	public void onEnable() {
		PaperPlatform plateforme = new PaperPlatform(this);
		Config config = Config.charger(plateforme);
		exportateur = new Exporter(plateforme, config);

		// Les événements se branchent AVANT le démarrage de l'exportateur : un compteur déclaré
		// mais jamais alimenté vaut mieux qu'une mort au chargement du serveur, et les deux
		// doivent exister quand le premier scrape arrive.
		evenements = new GameEventListener(exportateur.registre());
		if (config.collecteurActif("events", true)) {
			Bukkit.getPluginManager().registerEvents(evenements, this);
		}

		try {
			// Les collecteurs NATIFS sont passés ici et non tirés de la plateforme : ils sont au
			// noyau ce que les modules sont aux plugins tiers, et l'interface Platform, qui est
			// publique, n'a pas à les connaître.
			exportateur.demarrer(List.of(
					new TickCollector(), new WorldCollector(config), new PlayerCollector(config)));
			// LE NOYAU S'ANNONCE AUSSI DANS LE ServicesManager, qui est la voie idiomatique de
			// Bukkit. Le fournisseur statique de l'API reste la voie commune aux deux
			// plateformes — Velocity n'a pas de ServicesManager.
			Bukkit.getServicesManager().register(
					VaniaMetrics.class, exportateur, this, ServicePriority.Normal);
		} catch (Exception e) {
			// On désactive le plugin plutôt que de laisser un exportateur à moitié vivant :
			// un port déjà pris, par exemple, doit se voir tout de suite.
			getLogger().severe("démarrage impossible : " + e.getMessage());
			Bukkit.getPluginManager().disablePlugin(this);
		}
	}

	@Override
	public void onDisable() {
		Bukkit.getServicesManager().unregisterAll(this);
		if (exportateur != null) {
			exportateur.arreter();
		}
	}
}
