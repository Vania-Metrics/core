package fr.samflix.vaniametrics.paper;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.core.Exporter;

/**
 * Paper entry point. Builds the platform and starts the exporter; all the logic is in the common
 * core, and the Paper-specific collectors sit next to this class.
 */
public final class PaperPlugin extends JavaPlugin {

	private Exporter exporter;

	@Override
	public void onEnable() {
		PaperPlatform platform = new PaperPlatform(this);
		Config config = Config.load(platform);
		exporter = new Exporter(platform, config);

		// Listeners are registered before the exporter starts, so events during startup are
		// counted and the counters exist when the first scrape arrives.
		GameEventListener events = new GameEventListener(exporter.registry());
		if (config.isCollectorEnabled("events", true)) {
			Bukkit.getPluginManager().registerEvents(events, this);
		}

		try {
			// Platform collectors are passed in rather than exposed by Platform: that interface is
			// public API and has no business knowing them.
			exporter.start(List.of(
					new TickCollector(), new WorldCollector(config), new PlayerCollector(config)));
			// Also registered in the ServicesManager, Bukkit's idiomatic route. The static provider
			// remains the route shared by both platforms, since Velocity has no ServicesManager.
			Bukkit.getServicesManager().register(
					VaniaMetrics.class, exporter, this, ServicePriority.Normal);
		} catch (Exception e) {
			// Disable rather than leave a half-alive exporter: a port already in use, for
			// instance, should be noticed right away.
			getLogger().severe("failed to start: " + e.getMessage());
			Bukkit.getPluginManager().disablePlugin(this);
		}
	}

	@Override
	public void onDisable() {
		Bukkit.getServicesManager().unregisterAll(this);
		if (exporter != null) {
			exporter.stop();
		}
	}
}
