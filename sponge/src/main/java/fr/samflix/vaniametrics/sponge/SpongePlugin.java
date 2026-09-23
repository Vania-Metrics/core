package fr.samflix.vaniametrics.sponge;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Ticks;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import com.google.inject.Inject;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.core.Exporter;
import fr.samflix.vaniametrics.core.game.GameEvents;
import fr.samflix.vaniametrics.core.game.PlayerCollector;
import fr.samflix.vaniametrics.core.game.TickCollector;
import fr.samflix.vaniametrics.core.game.TpsMeter;
import fr.samflix.vaniametrics.core.game.WorldCollector;

/** Sponge entry point. Starts once the server is up, when worlds and the scheduler exist. */
@Plugin("vaniametrics")
public final class SpongePlugin {

	private final PluginContainer container;
	private final Logger logger;
	private final Path configDir;

	private Exporter exporter;

	@Inject
	SpongePlugin(PluginContainer container, Logger logger, @ConfigDir(sharedRoot = false) Path configDir) {
		this.container = container;
		this.logger = logger;
		this.configDir = configDir;
	}

	@Listener
	public void onStarted(StartedEngineEvent<Server> event) {
		Server server = event.engine();
		SpongePlatform platform = new SpongePlatform(container, logger, configDir);
		Config config = Config.load(platform);
		exporter = new Exporter(platform, config);

		// Sponge only exposes an instantaneous TPS; the 1/5/15 minute windows are measured here.
		TpsMeter tpsMeter = new TpsMeter();
		server.scheduler().submit(Task.builder()
				.plugin(container)
				.interval(Ticks.of(1))
				.execute(tpsMeter)
				.build());

		if (config.isCollectorEnabled("events", true)) {
			Sponge.eventManager().registerListeners(container,
					new SpongeEventListener(new GameEvents(exporter.registry())), MethodHandles.lookup());
		}

		SpongeGameServer game = new SpongeGameServer(server, tpsMeter);
		try {
			exporter.start(List.of(
					new TickCollector(game),
					new WorldCollector(game, config),
					new PlayerCollector(game, config)));
			platform.info("running on sponge");
		} catch (Exception e) {
			logger.error("failed to start: {}", e.getMessage());
		}
	}

	@Listener
	public void onStopping(StoppingEngineEvent<Server> event) {
		if (exporter != null) {
			exporter.stop();
		}
	}
}
