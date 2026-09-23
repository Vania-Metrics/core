package fr.samflix.vaniametrics.geyser;

import java.util.List;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.core.Exporter;
import fr.samflix.vaniametrics.core.proxy.ProxyCollector;
import fr.samflix.vaniametrics.core.proxy.ProxyEvents;

/**
 * Geyser extension: the Bedrock-to-Java proxy's own view. Works on every Geyser platform, but is
 * meant for Geyser standalone; when Geyser runs as a plugin next to VaniaMetrics, give the two
 * exporters different {@code http.port} values.
 */
public final class GeyserMetricsExtension implements Extension {

	private Exporter exporter;
	private GeyserPlatform platform;
	private ProxyEvents events;

	@Subscribe
	public void onPostInitialize(GeyserPostInitializeEvent event) {
		GeyserApi api = GeyserApi.api();
		platform = new GeyserPlatform(this);
		Config config = Config.load(platform);
		exporter = new Exporter(platform, config);
		if (config.isCollectorEnabled("events", true)) {
			events = new ProxyEvents(exporter.registry());
		}
		try {
			exporter.start(List.of(
					new ProxyCollector(new GeyserProxyServer(api), config),
					new BedrockCollector(api)));
			platform.info("running on geyser " + api.platformType().platformName());
		} catch (Exception e) {
			logger().error("failed to start: " + e.getMessage());
		}
	}

	@Subscribe
	public void onLogin(SessionLoginEvent event) {
		if (events != null) {
			events.preLogin();
		}
	}

	@Subscribe
	public void onJoin(SessionJoinEvent event) {
		if (events != null) {
			events.login();
		}
	}

	@Subscribe
	public void onDisconnect(SessionDisconnectEvent event) {
		if (events != null) {
			events.disconnect();
		}
	}

	@Subscribe
	public void onShutdown(GeyserShutdownEvent event) {
		if (exporter != null) {
			exporter.stop();
		}
		if (platform != null) {
			platform.close();
		}
	}
}
