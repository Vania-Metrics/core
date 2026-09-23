package fr.samflix.vaniametrics.geyser;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.extension.Extension;

import fr.samflix.vaniametrics.api.Platform;

final class GeyserPlatform implements Platform {

	private final Extension extension;
	// The extension API has no scheduler: background collectors get a thread of their own.
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "vaniametrics-background");
		t.setDaemon(true);
		return t;
	});

	GeyserPlatform(Extension extension) {
		this.extension = extension;
	}

	@Override
	public String type() {
		return "geyser";
	}

	@Override
	public String serverName() {
		String env = System.getenv("VANIA_SERVER_NAME");
		return env != null && !env.isEmpty() ? env : "geyser";
	}

	@Override
	public String serverVersion() {
		return "Geyser " + GeyserApi.api().platformType().platformName()
				+ " (API " + GeyserApi.api().geyserApiVersion() + ")";
	}

	@Override
	public Path dataDirectory() {
		return extension.dataFolder();
	}

	@Override
	public void info(String message) {
		extension.logger().info(message);
	}

	@Override
	public void warn(String message) {
		extension.logger().warning(message);
	}

	@Override
	public void error(String message, Throwable cause) {
		extension.logger().error(message, cause);
	}

	@Override
	public boolean isPluginPresent(String name) {
		return false;
	}

	@Override
	public void runOnMainThread(Runnable task) {
		// Geyser has no main thread to protect.
		task.run();
	}

	@Override
	public void scheduleRepeating(Runnable task, long intervalSeconds) {
		long seconds = Math.max(1, intervalSeconds);
		scheduler.scheduleAtFixedRate(task, seconds, seconds, TimeUnit.SECONDS);
	}

	void close() {
		scheduler.shutdownNow();
	}
}
