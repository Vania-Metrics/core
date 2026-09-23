package fr.samflix.vaniametrics.velocity;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;

import fr.samflix.vaniametrics.api.Platform;

final class VelocityPlatform implements Platform {

	private final ProxyServer proxy;
	private final Logger logger;
	private final Path dataDirectory;
	private final Object plugin;

	VelocityPlatform(ProxyServer proxy, Logger logger, Path dataDirectory, Object plugin) {
		this.proxy = proxy;
		this.logger = logger;
		this.dataDirectory = dataDirectory;
		this.plugin = plugin;
	}

	@Override
	public String type() {
		return "velocity";
	}

	@Override
	public String serverName() {
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : "proxy";
	}

	@Override
	public String serverVersion() {
		return proxy.getVersion().getName() + " " + proxy.getVersion().getVersion();
	}

	@Override
	public Path dataDirectory() {
		return dataDirectory;
	}

	@Override
	public void info(String message) {
		logger.info(message);
	}

	@Override
	public void warn(String message) {
		logger.warn(message);
	}

	@Override
	public void error(String message, Throwable cause) {
		logger.error(message, cause);
	}

	@Override
	public boolean isPluginPresent(String name) {
		PluginManager pm = proxy.getPluginManager();
		if (pm.getPlugin(name.toLowerCase(Locale.ROOT)).isPresent()) {
			return true;
		}
		// Velocity indexes plugins by id ("luckperms", lower case) where Bukkit uses the display
		// name ("LuckPerms"). A collector written for both platforms should not have to care, so
		// also match on the declared name.
		for (PluginContainer c : pm.getPlugins()) {
			if (c.getDescription().getName().filter(name::equalsIgnoreCase).isPresent()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void runOnMainThread(Runnable task) {
		// Velocity has no main thread and no game loop to protect: everything is asynchronous and
		// driven by Netty. Running in place is the correct translation, not a shortcut.
		task.run();
	}

	@Override
	public void scheduleRepeating(Runnable task, long intervalSeconds) {
		proxy.getScheduler()
				.buildTask(plugin, task)
				.repeat(intervalSeconds, TimeUnit.SECONDS)
				.delay(intervalSeconds, TimeUnit.SECONDS)
				.schedule();
	}
}
