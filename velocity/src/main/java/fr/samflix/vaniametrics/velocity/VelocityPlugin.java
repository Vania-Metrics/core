package fr.samflix.vaniametrics.velocity;

import java.nio.file.Path;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Version;
import fr.samflix.vaniametrics.core.Exporter;

/**
 * Le point d'entrée Velocity.
 *
 * <p>Symétrique de {@code PaperPlugin} : il construit la plateforme et démarre l'exportateur. Tout
 * le reste — registre, format, serveur HTTP, modules — est le même code que côté Paper.
 */
@Plugin(
		id = "vaniametrics",
		name = "VaniaMetrics",
		version = Version.VALEUR,
		description = "Expose les métriques du réseau au format Prometheus.",
		authors = {"mc-vania"})
public final class VelocityPlugin {

	private final ProxyServer proxy;
	private final Logger journal;
	private final Path repertoire;

	private Exporter exportateur;

	@Inject
	public VelocityPlugin(ProxyServer proxy, Logger journal, @DataDirectory Path repertoire) {
		this.proxy = proxy;
		this.journal = journal;
		this.repertoire = repertoire;
	}

	@Subscribe
	public void onInit(ProxyInitializeEvent e) {
		VelocityPlatform plateforme = new VelocityPlatform(proxy, journal, repertoire, this);
		Config config = Config.charger(plateforme);
		exportateur = new Exporter(plateforme, config);

		if (config.collecteurActif("events", true)) {
			proxy.getEventManager().register(this, new ProxyEventListener(exportateur.registre()));
		}

		try {
			exportateur.demarrer(java.util.List.of(new ProxyCollector(proxy, config)));
		} catch (Exception erreur) {
			journal.error("démarrage impossible", erreur);
		}
	}

	@Subscribe
	public void onShutdown(ProxyShutdownEvent e) {
		if (exportateur != null) {
			exportateur.arreter();
		}
	}
}
