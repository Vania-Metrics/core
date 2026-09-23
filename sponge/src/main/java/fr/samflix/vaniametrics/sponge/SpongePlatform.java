package fr.samflix.vaniametrics.sponge;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;

import fr.samflix.vaniametrics.api.Platform;

final class SpongePlatform implements Platform {

	private final PluginContainer container;
	private final Logger logger;
	private final Path configDir;

	SpongePlatform(PluginContainer container, Logger logger, Path configDir) {
		this.container = container;
		this.logger = logger;
		this.configDir = configDir;
	}

	@Override
	public String type() {
		return "sponge";
	}

	@Override
	public String serverName() {
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : "sponge";
	}

	@Override
	public String serverVersion() {
		return Sponge.platform().container(org.spongepowered.api.Platform.Component.IMPLEMENTATION)
				.metadata().version().toString();
	}

	@Override
	public Path dataDirectory() {
		return configDir;
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
		// Sponge plugin ids are lowercase.
		return Sponge.pluginManager().plugin(name.toLowerCase(java.util.Locale.ROOT)).isPresent();
	}

	@Override
	public void runOnMainThread(Runnable task) throws Exception {
		if (Sponge.server().onMainThread()) {
			task.run();
			return;
		}
		// Bounded wait: a frozen server must not block the exporter forever.
		CompletableFuture<Void> done = new CompletableFuture<>();
		Sponge.server().scheduler().submit(Task.builder()
				.plugin(container)
				.execute(() -> {
					try {
						task.run();
						done.complete(null);
					} catch (Throwable t) {
						done.completeExceptionally(t);
					}
				})
				.build());
		done.get(5, TimeUnit.SECONDS);
	}

	@Override
	public void scheduleRepeating(Runnable task, long intervalSeconds) {
		Duration interval = Duration.ofSeconds(Math.max(1, intervalSeconds));
		Sponge.asyncScheduler().submit(Task.builder()
				.plugin(container)
				.delay(interval)
				.interval(interval)
				.execute(task)
				.build());
	}
}
