package fr.samflix.vaniametrics.bukkit;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.core.Exporter;
import fr.samflix.vaniametrics.core.game.GameEvents;
import fr.samflix.vaniametrics.core.game.GameServer;
import fr.samflix.vaniametrics.core.game.PlayerCollector;
import fr.samflix.vaniametrics.core.game.TickCollector;
import fr.samflix.vaniametrics.core.game.TpsMeter;
import fr.samflix.vaniametrics.core.game.WorldCollector;

/**
 * Entry point for the Bukkit family: CraftBukkit, Spigot, Paper, Purpur, Folia. One jar; what each
 * server can provide is detected at startup (see {@link ServerFlavor}).
 */
public final class BukkitPlugin extends JavaPlugin {

	private Exporter exporter;
	private TpsMeter tpsMeter;

	@Override
	public void onEnable() {
		ServerFlavor flavor = ServerFlavor.detect();
		BukkitPlatform platform = new BukkitPlatform(this, flavor);
		Config config = Config.load(platform);
		exporter = new Exporter(platform, config);

		// Servers without their own TPS (CraftBukkit, Spigot, Folia) get ours, measured from the
		// ticks: the main thread's, or the global region's on Folia.
		if (!flavor.tickApi()) {
			tpsMeter = new TpsMeter();
			if (flavor.folia()) {
				Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, t -> tpsMeter.run(), 1, 1);
			} else {
				Bukkit.getScheduler().runTaskTimer(this, tpsMeter, 1, 1);
			}
		}
		GameServer server = flavor.folia()
				? new FoliaGameServer(this, flavor, tpsMeter)
				: new BukkitGameServer(flavor, tpsMeter);

		// Listeners are registered before the exporter starts, so events during startup are
		// counted and the counters exist when the first scrape arrives.
		if (config.isCollectorEnabled("events", true)) {
			Bukkit.getPluginManager().registerEvents(
					new BukkitEventListener(new GameEvents(exporter.registry())), this);
		}

		try {
			exporter.start(List.of(
					new TickCollector(server),
					new WorldCollector(server, config),
					new PlayerCollector(server, config)));
			Bukkit.getServicesManager().register(
					VaniaMetrics.class, exporter, this, ServicePriority.Normal);
			platform.info("running on " + flavor.name()
					+ (flavor.tickApi() ? "" : ", TPS measured by VaniaMetrics"));
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
