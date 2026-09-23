package fr.samflix.vaniametrics.velocity;

import java.nio.file.Path;
import java.util.List;

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
import fr.samflix.vaniametrics.core.proxy.ProxyCollector;
import fr.samflix.vaniametrics.core.proxy.ProxyEvents;

/**
 * Velocity entry point. Builds the platform and starts the exporter; the proxy collector, the
 * event counters, the registry and the HTTP server are shared with every other proxy loader.
 */
@Plugin(
		id = "vaniametrics",
		name = "VaniaMetrics",
		version = Version.VALUE,
		description = "Exposes network metrics in the Prometheus format.",
		authors = {"mc-vania"})
public final class VelocityPlugin {

	private final ProxyServer proxy;
	private final Logger logger;
	private final Path dataDirectory;

	private Exporter exporter;

	@Inject
	public VelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
		this.proxy = proxy;
		this.logger = logger;
		this.dataDirectory = dataDirectory;
	}

	@Subscribe
	public void onInit(ProxyInitializeEvent e) {
		VelocityPlatform platform = new VelocityPlatform(proxy, logger, dataDirectory, this);
		Config config = Config.load(platform);
		exporter = new Exporter(platform, config);

		if (config.isCollectorEnabled("events", true)) {
			proxy.getEventManager().register(this, new ProxyEventListener(new ProxyEvents(exporter.registry())));
		}

		try {
			exporter.start(List.of(new ProxyCollector(new VelocityProxyServer(proxy), config)));
		} catch (Exception error) {
			logger.error("failed to start", error);
		}
	}

	@Subscribe
	public void onShutdown(ProxyShutdownEvent e) {
		if (exporter != null) {
			exporter.stop();
		}
	}
}
