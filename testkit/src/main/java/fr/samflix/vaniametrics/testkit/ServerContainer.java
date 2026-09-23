package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.DockerClient;

/**
 * A Minecraft server or proxy in a container, with the plugin jars installed.
 *
 * <p>Jars are copied in before start, never bind-mounted: copies need no SELinux label and work
 * the same under Docker and rootless Podman. The log is streamed to a file as it comes, so a cell
 * that hangs still leaves something to read.
 *
 * <p>Stopping is the other half of the test. {@link GenericContainer#stop()} kills the process,
 * which would hide a plugin that fails while disabling; {@link #stopGracefully()} sends SIGTERM,
 * which the image turns into a {@code stop} command, and waits for the server to exit on its own.
 */
public final class ServerContainer implements AutoCloseable {

	/** The plugin's metrics port inside the container. */
	public static final int METRICS_PORT = 9940;
	/** The Minecraft port inside the container. */
	public static final int GAME_PORT = 25565;

	private static final Pattern[] BUILD_LINES = {
		Pattern.compile("This server is running (.+?) \\(Implementing"),
		Pattern.compile("This server is running (.+)"),
		Pattern.compile("Booting up (Velocity \\S+)"),
		Pattern.compile("Enabled (\\S+ version \\S+)"),
		Pattern.compile("Loading (Geyser version \\S+)"),
		Pattern.compile("(spongevanilla-\\S+?)(?:-universal)?\\.jar"),
	};

	private final GenericContainer<?> container;
	private final Path logFile;
	private String finalLog;
	private Long exitCode;

	private ServerContainer(GenericContainer<?> container, Path logFile) {
		this.container = container;
		this.logFile = logFile;
	}

	/**
	 * A Bukkit-family game server from the itzg image: Paper, Purpur, Folia.
	 *
	 * @param plugins jars to install
	 * @param env extra environment, e.g. {@code VANIA_METRICS_*} settings
	 * @param cell a name for the log file
	 */
	public static ServerContainer game(Variant variant, DockerImageName image, List<Path> plugins,
			Map<String, String> env, Network network, String alias, String cell) {
		Map<String, String> all = new HashMap<>(gameDefaults());
		all.putAll(variant.env());
		all.putAll(env);
		GenericContainer<?> c = new GenericContainer<>(image)
				.withEnv(all)
				.withExposedPorts(METRICS_PORT, GAME_PORT)
				.withNetwork(network)
				.withNetworkAliases(alias)
				.waitingFor(Wait.forLogMessage("(?s).*Done \\(.*", 1)
						.withStartupTimeout(Duration.ofMinutes(8)));
		for (Path jar : plugins) {
			c.withCopyFileToContainer(MountableFile.forHostPath(jar, 0644), "/plugins/" + jar.getFileName());
		}
		return new ServerContainer(c, logFile(cell));
	}

	/**
	 * What every game server gets: a small flat world, offline mode for the bots, and short
	 * collection intervals so a background collector runs within seconds instead of minutes.
	 */
	private static Map<String, String> gameDefaults() {
		Map<String, String> env = new HashMap<>();
		env.put("EULA", "TRUE");
		env.put("ONLINE_MODE", "FALSE");
		env.put("MEMORY", "1G");
		env.put("LEVEL_TYPE", "FLAT");
		env.put("VIEW_DISTANCE", "4");
		env.put("SIMULATION_DISTANCE", "4");
		env.put("SPAWN_PROTECTION", "0");
		// C1 only: starts faster, and nothing here runs long enough to need C2.
		env.put("JVM_XX_OPTS", "-XX:TieredStopAtLevel=1");
		env.put("VANIA_METRICS_COLLECTOR_WORLD_INTERVAL", "2");
		env.put("VANIA_METRICS_COLLECTOR_DISK_INTERVAL", "5");
		return env;
	}

	private static Path logFile(String cell) {
		return Harness.reports().resolve("logs").resolve(cell.replaceAll("[^A-Za-z0-9._-]", "_") + ".log");
	}

	/** Starts the container and waits until the server says it is done and the plugin answers. */
	public ServerContainer start() {
		try {
			Files.writeString(logFile, "", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		container.withLogConsumer(frame -> {
			try {
				Files.writeString(logFile, frame.getUtf8String(), StandardCharsets.UTF_8,
						StandardOpenOption.APPEND);
			} catch (IOException e) {
				// A lost log line must not fail the test; the final log is read again on stop.
			}
		});
		container.start();
		try {
			metrics().awaitStatus("/healthz", Duration.ofSeconds(60));
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + ": the server started but the plugin is not serving "
					+ "(not installed, or failed to enable). Last log lines:\n" + tail(container.getLogs(), 40), e);
		}
		return this;
	}

	private static String tail(String log, int lines) {
		List<String> all = LogAudit.clean(log).lines().toList();
		return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
	}

	public MetricsEndpoint metrics() {
		return new MetricsEndpoint(container.getHost(), container.getMappedPort(METRICS_PORT));
	}

	/** The log up to now. */
	public String log() {
		return finalLog != null ? finalLog : container.getLogs();
	}

	/** The server build as the log states it, for the report. */
	public String build() {
		String log = LogAudit.clean(log());
		for (Pattern p : BUILD_LINES) {
			Matcher m = p.matcher(log);
			if (m.find()) {
				return m.group(1).trim();
			}
		}
		return "?";
	}

	/**
	 * Stops the server the way an operator would, and keeps its full log.
	 *
	 * @return the exit code; 0 when the server shut down on its own
	 */
	public long stopGracefully() {
		if (exitCode != null) {
			return exitCode;
		}
		DockerClient docker = container.getDockerClient();
		String id = container.getContainerId();
		docker.stopContainerCmd(id).withTimeout(90).exec();
		Long code = docker.inspectContainerCmd(id).exec().getState().getExitCodeLong();
		exitCode = code == null ? -1 : code;
		finalLog = container.getLogs();
		try {
			Files.writeString(logFile, finalLog, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return exitCode;
	}

	@Override
	public void close() {
		if (finalLog == null && container.getContainerId() != null) {
			// Kept for the report, which reads the build from it once the container is gone.
			try {
				finalLog = container.getLogs();
			} catch (RuntimeException e) {
				finalLog = "";
			}
		}
		container.stop();
	}
}
