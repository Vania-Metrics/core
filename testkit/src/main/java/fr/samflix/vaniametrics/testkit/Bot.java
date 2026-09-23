package fr.samflix.vaniametrics.testkit;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * A player, in a container on the servers' network: mineflayer for Java Edition,
 * bedrock-protocol for Bedrock through Geyser.
 *
 * <p>It joins, stays the given time and leaves on its own: the test scrapes while it is online,
 * then again once it has left. Bedrock speaks UDP, which Testcontainers cannot map to the host:
 * joining over the containers' network avoids the question for both.
 */
public final class Bot implements AutoCloseable {

	private final GenericContainer<?> container;

	private Bot(GenericContainer<?> container) {
		this.container = container;
	}

	/** Built from the testkit's resources; the layer cache makes rebuilds instant. */
	private static ImageFromDockerfile image(String edition) {
		String dir = "bots/" + edition + "/";
		return new ImageFromDockerfile("localhost/vania-metrics-" + edition + "-bot:1", false)
				.withFileFromClasspath("Dockerfile", dir + "Dockerfile")
				.withFileFromClasspath("package.json", dir + "package.json")
				.withFileFromClasspath("package-lock.json", dir + "package-lock.json")
				.withFileFromClasspath("bot.js", dir + "bot.js");
	}

	/**
	 * A Java Edition player; returns once it is in the world.
	 *
	 * @param host the server's network alias
	 * @param minecraft the game version to speak: {@code 1.21.11}
	 * @param stay how long to stay before leaving
	 */
	public static Bot java(Network network, String host, int port, String minecraft, String name, Duration stay) {
		return start(new GenericContainer<>(image("java"))
				.withEnv("MC_VERSION", minecraft), network, host, port, name, stay);
	}

	/** A Bedrock player, offline, joining a Geyser; returns once it is in the world. */
	public static Bot bedrock(Network network, String host, int port, String name, Duration stay) {
		return start(new GenericContainer<>(image("bedrock")), network, host, port, name, stay);
	}

	private static Bot start(GenericContainer<?> c, Network network, String host, int port, String name,
			Duration stay) {
		c.withNetwork(network)
				.withEnv("SERVER_HOST", host)
				.withEnv("SERVER_PORT", String.valueOf(port))
				.withEnv("BOT_NAME", name)
				.withEnv("STAY_SECONDS", String.valueOf(stay.toSeconds()))
				.waitingFor(Wait.forLogMessage("(?s).*BOT_SPAWNED.*", 1)
						.withStartupTimeout(Duration.ofMinutes(3)));
		try {
			Containers.start(c);
		} catch (RuntimeException e) {
			// Testcontainers says the bot never spawned; the bot's own output says why.
			String log;
			try {
				log = Containers.call(c::getLogs);
			} catch (RuntimeException ignored) {
				log = "(no output)";
			}
			Containers.remove(c);
			throw new AssertionError("the bot never got into the world:\n" + log, e);
		}
		return new Bot(c);
	}

	/** Waits for the bot to leave on its own, and checks it did so cleanly. */
	public void awaitLeft(Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (Containers.call(container::isRunning) && System.nanoTime() < deadline) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted", e);
			}
		}
		if (Containers.call(container::isRunning)) {
			throw new AssertionError("the bot is still connected after " + timeout.toSeconds() + " s:\n"
					+ Containers.call(container::getLogs));
		}
		Long code = Containers.call(container::getCurrentContainerInfo).getState().getExitCodeLong();
		if (code == null || code != 0) {
			throw new AssertionError("the bot exited with " + code + ":\n" + Containers.call(container::getLogs));
		}
	}

	public String log() {
		return Containers.call(container::getLogs);
	}

	@Override
	public void close() {
		Containers.remove(container);
	}
}
