package fr.samflix.vaniametrics.bukkit;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.Platform;

final class BukkitPlatform implements Platform {

	private final JavaPlugin plugin;
	private final ServerFlavor flavor;

	BukkitPlatform(JavaPlugin plugin, ServerFlavor flavor) {
		this.plugin = plugin;
		this.flavor = flavor;
	}

	@Override
	public String type() {
		return flavor.name();
	}

	@Override
	public String serverName() {
		// From the environment rather than server.properties: the deployment knows what this
		// instance is called on the network, and it is the same name its other labels carry.
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : flavor.name();
	}

	@Override
	public String serverVersion() {
		return Bukkit.getVersion();
	}

	@Override
	public Path dataDirectory() {
		return plugin.getDataFolder().toPath();
	}

	@Override
	public void info(String message) {
		plugin.getLogger().info(message);
	}

	@Override
	public void warn(String message) {
		plugin.getLogger().warning(message);
	}

	@Override
	public void error(String message, Throwable cause) {
		plugin.getLogger().log(Level.SEVERE, message, cause);
	}

	@Override
	public boolean isPluginPresent(String name) {
		return Bukkit.getPluginManager().getPlugin(name) != null;
	}

	@Override
	public <T> Optional<T> service(Class<T> type) {
		return Optional.ofNullable(Bukkit.getServicesManager().load(type));
	}

	@Override
	public void runOnMainThread(Runnable task) throws Exception {
		if (!flavor.folia() && Bukkit.isPrimaryThread()) {
			task.run();
			return;
		}
		// The waiting thread is a background task thread, never the server's or the HTTP one.
		// The timeout bounds the wait: a frozen server must not block the exporter forever,
		// or the metric showing it is frozen would never arrive.
		CompletableFuture<Void> done = new CompletableFuture<>();
		Runnable wrapped = () -> {
			try {
				task.run();
				done.complete(null);
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		};
		if (flavor.folia()) {
			// No main thread on Folia; the global region is the closest thing, and owns
			// server-wide state. Region-bound data must be read by the caller on its own region.
			Bukkit.getGlobalRegionScheduler().execute(plugin, wrapped);
		} else {
			Bukkit.getScheduler().runTask(plugin, wrapped);
		}
		done.get(5, TimeUnit.SECONDS);
	}

	@Override
	public void scheduleRepeating(Runnable task, long intervalSeconds) {
		long seconds = Math.max(1, intervalSeconds);
		if (flavor.folia()) {
			// Bukkit's scheduler throws on Folia.
			Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), seconds, seconds, TimeUnit.SECONDS);
		} else {
			Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, seconds * 20, seconds * 20);
		}
	}
}
