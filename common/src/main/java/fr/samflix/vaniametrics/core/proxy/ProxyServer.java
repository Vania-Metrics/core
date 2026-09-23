package fr.samflix.vaniametrics.core.proxy;

import java.util.List;

/**
 * What a proxy loader (Velocity, BungeeCord, Geyser...) exposes to the common proxy collector.
 * Called from a background thread; proxies have no main thread to respect.
 */
public interface ProxyServer {

	int playerCount();

	List<ProxyPlayer> players();

	/** Backend servers the proxy can send players to; empty when the loader has none to report. */
	List<Backend> backends();

	/**
	 * Pings a backend and waits for the answer.
	 *
	 * @return the player slots it advertises, or {@code -1} if its answer has none
	 * @throws Exception if it does not answer in time, for any reason
	 */
	int ping(String backend, long timeoutSeconds) throws Exception;

	/** @param brand client brand, {@code null} when unknown or not exposed */
	record ProxyPlayer(int pingMillis, String brand) {
	}

	record Backend(String name, int players) {
	}
}
