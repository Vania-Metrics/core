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
 * which the image turns into a stop command, and waits for the server to exit on its own.
 */
public final class ServerContainer implements AutoCloseable {

	/** The plugin's metrics port inside the container. */
	public static final int METRICS_PORT = 9940;
	/** The Minecraft port of a game server inside the container. */
	public static final int GAME_PORT = 25565;
	/** The Minecraft port of a proxy inside the container. */
	public static final int PROXY_PORT = 25577;
	/** Geyser's Bedrock port (UDP) inside the container. */
	public static final int BEDROCK_PORT = 19132;

	private static final Pattern[] BUILD_LINES = {
		Pattern.compile("This server is running (.+?) \\(Implementing"),
		Pattern.compile("This server is running (.+)"),
		Pattern.compile("Booting up (Velocity \\S+)"),
		Pattern.compile("Enabled (\\S+ version \\S+)"),
		Pattern.compile("Loading (Geyser version \\S+)"),
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
	 * A game server from the itzg image: every Bukkit fork, and Sponge.
	 *
	 * @param image the itzg image, which fixes the Java version
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
				.withExposedPorts(METRICS_PORT, GAME_PORT);
		return common(c, variant, plugins, network, alias, cell);
	}

	/** A proxy from the itzg mc-proxy image: Velocity, BungeeCord, Waterfall. */
	public static ServerContainer proxy(Variant variant, List<Path> plugins, Map<String, String> env,
			Network network, String alias, String cell) {
		Map<String, String> all = new HashMap<>();
		all.put("MEMORY", "512m");
		all.put("VANIA_METRICS_COLLECTOR_PROXY_INTERVAL", "2");
		all.put("VANIA_METRICS_COLLECTOR_PROXY_PING_TIMEOUT", "2");
		all.put("VANIA_METRICS_COLLECTOR_DISK_INTERVAL", "5");
		all.putAll(variant.env());
		all.putAll(env);
		GenericContainer<?> c = new GenericContainer<>(variant.java() >= 25 ? Images.PROXY_JAVA25 : Images.PROXY_JAVA21)
				.withEnv(all)
				.withExposedPorts(METRICS_PORT, PROXY_PORT);
		return common(c, variant, plugins, network, alias, cell);
	}

	/**
	 * Geyser Standalone on a plain JRE: no image runs it. Extensions go to {@code extensions/};
	 * Bedrock is UDP, which Testcontainers cannot map: bots join over the containers' network.
	 */
	public static ServerContainer geyser(Variant variant, List<Path> extensions, Map<String, String> env,
			Network network, String alias, String cell) {
		Map<String, String> all = new HashMap<>();
		all.put("VANIA_METRICS_COLLECTOR_PROXY_INTERVAL", "2");
		all.put("VANIA_METRICS_COLLECTOR_DISK_INTERVAL", "5");
		all.putAll(env);
		GenericContainer<?> c = new GenericContainer<>(Images.TEMURIN21)
				.withEnv(all)
				.withWorkingDirectory("/data")
				.withCommand("java", "-Xmx512m", "-XX:TieredStopAtLevel=1", "-jar",
						"/server-jars/" + variant.serverJar().fileName(), "--nogui")
				.withExposedPorts(METRICS_PORT);
		return common(c, variant, extensions, "/data/extensions", network, alias, cell);
	}

	private static ServerContainer common(GenericContainer<?> c, Variant variant, List<Path> plugins,
			Network network, String alias, String cell) {
		return common(c, variant, plugins, "/plugins", network, alias, cell);
	}

	private static ServerContainer common(GenericContainer<?> c, Variant variant, List<Path> plugins,
			String pluginDir, Network network, String alias, String cell) {
		c.withNetwork(network)
				.withNetworkAliases(alias)
				.waitingFor(Wait.forLogMessage(variant.ready(), 1).withStartupTimeout(Duration.ofMinutes(8)));
		for (Path jar : plugins) {
			c.withCopyFileToContainer(MountableFile.forHostPath(jar, 0644), pluginDir + "/" + jar.getFileName());
		}
		variant.files().forEach((resource, path) ->
				c.withCopyFileToContainer(MountableFile.forClasspathResource(resource, 0644), path));
		if (variant.serverJar() != null) {
			c.withCopyFileToContainer(MountableFile.forHostPath(variant.serverJar().source().get(), 0644),
					"/server-jars/" + variant.serverJar().fileName());
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

	/** Starts the container and waits until the server is up and the plugin answers. */
	public ServerContainer start() {
		startServer();
		try {
			metrics().awaitStatus("/healthz", Duration.ofSeconds(60));
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + ": the server started but the plugin is not serving "
					+ "(not installed, or failed to enable). Last log lines:\n" + tail(container.getLogs(), 40), e);
		}
		return this;
	}

	/** Starts a server that runs no plugin of ours: a proxy's backend. */
	public ServerContainer startServer() {
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
		Containers.start(container);
		return this;
	}

	/**
	 * The port the server says it listens on. The proxy image decides it, not our config:
	 * mc-proxy starts Velocity on 25565 whatever velocity.toml says, BungeeCord on 25577.
	 */
	public int listeningPort() {
		Matcher m = Pattern.compile("Listening on /\\S*:(\\d+)").matcher(LogAudit.clean(log()));
		if (!m.find()) {
			throw new AssertionError("the log never says which port the server listens on");
		}
		return Integer.parseInt(m.group(1));
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
		return finalLog != null ? finalLog : Containers.call(container::getLogs);
	}

	/**
	 * The server build as the log states it, for the report, or null when the log does not say
	 * (Sponge): the pinned build then stands for it.
	 */
	public String build() {
		String log = LogAudit.clean(log());
		for (Pattern p : BUILD_LINES) {
			Matcher m = p.matcher(log);
			if (m.find()) {
				return m.group(1).trim();
			}
		}
		return null;
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
		Containers.call(() -> docker.stopContainerCmd(id).withTimeout(90).exec());
		Long code = Containers.call(() -> docker.inspectContainerCmd(id).exec()).getState().getExitCodeLong();
		exitCode = code == null ? -1 : code;
		finalLog = Containers.call(container::getLogs);
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
				finalLog = Containers.call(container::getLogs);
			} catch (RuntimeException e) {
				finalLog = "";
			}
		}
		Containers.remove(container);
	}
}
