package fr.samflix.vaniametrics.bungee;

import java.util.List;

import net.md_5.bungee.api.plugin.Plugin;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.core.Exporter;
import fr.samflix.vaniametrics.core.proxy.ProxyCollector;
import fr.samflix.vaniametrics.core.proxy.ProxyEvents;

/** Entry point for BungeeCord and its forks (Waterfall). */
public final class BungeePlugin extends Plugin {

	private Exporter exporter;

	@Override
	public void onEnable() {
		BungeePlatform platform = new BungeePlatform(this);
		Config config = Config.load(platform);
		exporter = new Exporter(platform, config);

		if (config.isCollectorEnabled("events", true)) {
			getProxy().getPluginManager().registerListener(this,
					new BungeeEventListener(new ProxyEvents(exporter.registry())));
		}

		try {
			exporter.start(List.of(new ProxyCollector(new BungeeProxyServer(getProxy()), config)));
			platform.info("running on " + platform.type());
		} catch (Exception e) {
			getLogger().severe("failed to start: " + e.getMessage());
		}
	}

	@Override
	public void onDisable() {
		if (exporter != null) {
			exporter.stop();
		}
	}
}
