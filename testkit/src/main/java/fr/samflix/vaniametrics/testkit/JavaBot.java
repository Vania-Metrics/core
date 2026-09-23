package fr.samflix.vaniametrics.testkit;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * A Java Edition player: mineflayer in a container on the servers' network.
 *
 * <p>It joins, jumps now and then so the server sees movement, and leaves on its own after the
 * given time: the test scrapes while it is online, then again once it has left.
 */
public final class JavaBot implements AutoCloseable {

	private static final String IMAGE = "localhost/vania-metrics-java-bot:1";

	private final GenericContainer<?> container;

	private JavaBot(GenericContainer<?> container) {
		this.container = container;
	}

	/** Built from the testkit's resources; the layer cache makes rebuilds instant. */
	private static ImageFromDockerfile image() {
		return new ImageFromDockerfile(IMAGE, false)
				.withFileFromClasspath("Dockerfile", "bots/java/Dockerfile")
				.withFileFromClasspath("package.json", "bots/java/package.json")
				.withFileFromClasspath("package-lock.json", "bots/java/package-lock.json")
				.withFileFromClasspath("bot.js", "bots/java/bot.js");
	}

	/**
	 * Joins and returns once the player is in the world.
	 *
	 * @param host the server's network alias
	 * @param stay how long to stay before leaving
	 */
	public static JavaBot join(Network network, String host, int port, String name, Duration stay) {
		GenericContainer<?> c = new GenericContainer<>(image())
				.withNetwork(network)
				.withEnv("SERVER_HOST", host)
				.withEnv("SERVER_PORT", String.valueOf(port))
				.withEnv("BOT_NAME", name)
				.withEnv("MC_VERSION", "1.21.11")
				.withEnv("STAY_SECONDS", String.valueOf(stay.toSeconds()))
				.waitingFor(Wait.forLogMessage("(?s).*BOT_SPAWNED.*", 1)
						.withStartupTimeout(Duration.ofMinutes(3)));
		c.start();
		return new JavaBot(c);
	}

	/** Waits for the bot to leave on its own, and checks it did so cleanly. */
	public void awaitLeft(Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (container.isRunning() && System.nanoTime() < deadline) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted", e);
			}
		}
		if (container.isRunning()) {
			throw new AssertionError("the bot is still connected after " + timeout.toSeconds() + " s:\n"
					+ container.getLogs());
		}
		Long code = container.getCurrentContainerInfo().getState().getExitCodeLong();
		if (code == null || code != 0) {
			throw new AssertionError("the bot exited with " + code + ":\n" + container.getLogs());
		}
	}

	public String log() {
		return container.getLogs();
	}

	@Override
	public void close() {
		container.stop();
	}
}
