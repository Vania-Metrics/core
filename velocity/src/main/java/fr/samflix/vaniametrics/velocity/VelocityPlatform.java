package fr.samflix.vaniametrics.velocity;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;

import fr.samflix.vaniametrics.api.Platform;

/** L'adaptateur Velocity. Même contrat que côté Paper, un monde différent derrière. */
final class VelocityPlatform implements Platform {

	private final ProxyServer proxy;
	private final Logger journal;
	private final Path repertoire;
	private final Object plugin;

	VelocityPlatform(ProxyServer proxy, Logger journal, Path repertoire, Object plugin) {
		this.proxy = proxy;
		this.journal = journal;
		this.repertoire = repertoire;
		this.plugin = plugin;
	}

	ProxyServer proxy() {
		return proxy;
	}

	@Override
	public String type() {
		return "velocity";
	}

	@Override
	public String nomServeur() {
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : "proxy";
	}

	@Override
	public String versionServeur() {
		return proxy.getVersion().getName() + " " + proxy.getVersion().getVersion();
	}

	@Override
	public Path repertoire() {
		return repertoire;
	}

	@Override
	public void info(String message) {
		journal.info(message);
	}

	@Override
	public void avertir(String message) {
		journal.warn(message);
	}

	@Override
	public void erreur(String message, Throwable cause) {
		journal.error(message, cause);
	}

	@Override
	public boolean pluginPresent(String nom) {
		PluginManager pm = proxy.getPluginManager();
		if (pm.getPlugin(nom.toLowerCase(java.util.Locale.ROOT)).isPresent()) {
			return true;
		}
		// Velocity indexe ses plugins par IDENTIFIANT — « luckperms », en minuscules — là où
		// Bukkit les indexe par NOM affiché — « LuckPerms ». Un module écrit pour les deux
		// plateformes ne doit pas avoir à le savoir : on cherche aussi par nom déclaré.
		for (PluginContainer c : pm.getPlugins()) {
			if (c.getDescription().getName().filter(nom::equalsIgnoreCase).isPresent()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void surFilPrincipal(Runnable tache) {
		// VELOCITY N'A PAS DE FIL PRINCIPAL. Il n'y a pas de boucle de jeu à protéger : tout est
		// asynchrone et piloté par Netty. Exécuter sur place est donc la traduction JUSTE de
		// « sur le fil principal », et non un raccourci.
		tache.run();
	}

	@Override
	public void repeter(Runnable tache, long intervalleSecondes) {
		proxy.getScheduler()
				.buildTask(plugin, tache)
				.repeat(intervalleSecondes, TimeUnit.SECONDS)
				.delay(intervalleSecondes, TimeUnit.SECONDS)
				.schedule();
	}

}
