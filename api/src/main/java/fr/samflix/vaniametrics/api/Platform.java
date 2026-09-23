package fr.samflix.vaniametrics.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * What the core needs from its host platform.
 *
 * <p>This is the whole boundary between the core and the platform adapters. The core knows
 * neither Bukkit nor Velocity, only this interface, which each adapter implements. It is small on
 * purpose: every method added here is one more thing to implement twice and one more chance for
 * the platforms to diverge.
 *
 * <p>It lives in the API because collectors need it too: to log to the right place, to schedule a
 * task, and to look up a service without knowing whether the platform has a ServicesManager.
 * Collectors never implement it; they receive it.
 */
public interface Platform {

	/** "paper" or "velocity". Used as a label in {@code mc_build_info}. */
	String type();

	/** This instance's name on the network: "lobby", "proxy". The {@code server} label. */
	String serverName();

	/** The server software version, for {@code mc_build_info}. */
	String serverVersion();

	/** The plugin's configuration directory. */
	Path dataDirectory();

	void info(String message);

	void warn(String message);

	void error(String message, Throwable cause);

	/** Whether a plugin is installed. Lets a collector enable itself only when it has something to read. */
	boolean isPluginPresent(String name);

	/**
	 * Looks up a service provided by another plugin.
	 *
	 * <p>On Paper this is Bukkit's {@code ServicesManager}, where LuckPerms, Vault, spark and
	 * EssentialsX register their entry points. Velocity has no equivalent; plugins there expose a
	 * static provider instead.
	 *
	 * <p>Added after a real surprise: {@code SparkProvider.get()} returns nothing on Paper, because
	 * spark is built into the server there and registers through the {@code ServicesManager}. A
	 * collector that works on both platforms has to try both routes, and only the platform knows
	 * which one it has.
	 */
	default <T> Optional<T> service(Class<T> type) {
		return Optional.empty();
	}

	/**
	 * Runs on the server's main thread and waits for completion.
	 *
	 * <p>Velocity has no main thread, so the implementation runs the task in place. Paper goes
	 * through the scheduler and blocks the calling thread, which is a background task thread,
	 * never the server's.
	 */
	void runOnMainThread(Runnable task) throws Exception;

	/** Schedules a repeating task off the main thread. */
	void scheduleRepeating(Runnable task, long intervalSeconds);
}
