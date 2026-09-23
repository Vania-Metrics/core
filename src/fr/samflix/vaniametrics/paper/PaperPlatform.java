package fr.samflix.vaniametrics.paper;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.Platform;

/** L'adaptateur Paper : tout ce que le noyau demande, traduit en Bukkit. */
final class PaperPlatform implements Platform {

	private final JavaPlugin plugin;

	PaperPlatform(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String type() {
		return "paper";
	}

	@Override
	public String nomServeur() {
		// Le nom vient de l'environnement et non de server.properties : c'est le chart qui sait
		// comment s'appelle cette instance dans le réseau, et c'est le même nom que les
		// étiquettes Kubernetes portent.
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : "paper";
	}

	@Override
	public String versionServeur() {
		return Bukkit.getVersion();
	}

	@Override
	public Path repertoire() {
		return plugin.getDataFolder().toPath();
	}

	@Override
	public void info(String message) {
		plugin.getLogger().info(message);
	}

	@Override
	public void avertir(String message) {
		plugin.getLogger().warning(message);
	}

	@Override
	public void erreur(String message, Throwable cause) {
		plugin.getLogger().log(Level.SEVERE, message, cause);
	}

	@Override
	public boolean pluginPresent(String nom) {
		return Bukkit.getPluginManager().getPlugin(nom) != null;
	}

	@Override
	public <T> java.util.Optional<T> service(Class<T> type) {
		return java.util.Optional.ofNullable(Bukkit.getServicesManager().load(type));
	}

	@Override
	public void surFilPrincipal(Runnable tache) throws Exception {
		if (Bukkit.isPrimaryThread()) {
			tache.run();
			return;
		}
		// On ATTEND le résultat, et le fil qui attend est celui d'une tâche de fond — jamais
		// celui du serveur, jamais celui du HTTP. Le délai borne l'attente : un serveur figé ne
		// doit pas bloquer l'exportateur pour toujours, sinon la métrique qui dirait qu'il est
		// figé n'arriverait jamais.
		CompletableFuture<Void> fini = new CompletableFuture<>();
		Bukkit.getScheduler().runTask(plugin, () -> {
			try {
				tache.run();
				fini.complete(null);
			} catch (Throwable t) {
				fini.completeExceptionally(t);
			}
		});
		fini.get(5, TimeUnit.SECONDS);
	}

	@Override
	public void repeter(Runnable tache, long intervalleSecondes) {
		long ticks = Math.max(1, intervalleSecondes * 20);
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, tache, ticks, ticks);
	}

}
