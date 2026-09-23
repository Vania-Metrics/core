package fr.samflix.vaniametrics.bungee;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.md_5.bungee.api.plugin.Plugin;

import fr.samflix.vaniametrics.api.Platform;

final class BungeePlatform implements Platform {

	private final Plugin plugin;
	private final String type;

	BungeePlatform(Plugin plugin) {
		this.plugin = plugin;
		// "BungeeCord" or a fork's own name, such as "Waterfall".
		this.type = plugin.getProxy().getName().toLowerCase(Locale.ROOT);
	}

	@Override
	public String type() {
		return type;
	}

	@Override
	public String serverName() {
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : "proxy";
	}

	@Override
	public String serverVersion() {
		return plugin.getProxy().getVersion();
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
		return plugin.getProxy().getPluginManager().getPlugin(name) != null;
	}

	@Override
	public void runOnMainThread(Runnable task) {
		// BungeeCord has no main thread to protect: everything is asynchronous, driven by Netty.
		task.run();
	}

	@Override
	public void scheduleRepeating(Runnable task, long intervalSeconds) {
		long seconds = Math.max(1, intervalSeconds);
		plugin.getProxy().getScheduler().schedule(plugin, task, seconds, seconds, TimeUnit.SECONDS);
	}
}
