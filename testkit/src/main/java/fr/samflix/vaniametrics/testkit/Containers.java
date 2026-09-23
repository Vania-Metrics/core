package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.util.function.Supplier;

import org.testcontainers.containers.GenericContainer;

/**
 * Calls to the Docker API, made to survive Podman.
 *
 * <p>Podman's API server drops keep-alive connections that sat idle for a few seconds, and the
 * client may pick one of those from its pool and fail with "broken pipe" before Podman has even
 * read the request. That is the transport, not the server under test: every call the harness
 * makes is retried once, on a fresh connection, and only for an I/O failure. A server that fails
 * to start fails the same way twice and is reported. Docker does not do this; there the retry
 * never fires.
 */
final class Containers {

	private Containers() {}

	/** Starts a container; once more if the connection broke underneath. */
	static void start(GenericContainer<?> container) {
		try {
			container.start();
		} catch (RuntimeException e) {
			if (!brokenConnection(e)) {
				throw e;
			}
			System.out.println("Docker API connection broken (" + rootCause(e).getMessage() + "), retrying once");
			container.stop();
			container.start();
		}
	}

	/** A Docker API call; once more if the connection broke underneath. */
	static <T> T call(Supplier<T> call) {
		try {
			return call.get();
		} catch (RuntimeException e) {
			if (!brokenConnection(e)) {
				throw e;
			}
			return call.get();
		}
	}

	/** Stops and removes a container, never failing the test for it: the reaper removes leftovers. */
	static void remove(GenericContainer<?> container) {
		try {
			call(() -> {
				container.stop();
				return null;
			});
		} catch (RuntimeException e) {
			System.out.println("could not remove container " + container.getContainerId() + ": " + e);
		}
	}

	private static boolean brokenConnection(Throwable e) {
		return rootCause(e) instanceof IOException;
	}

	private static Throwable rootCause(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null && c.getCause() != c) {
			c = c.getCause();
		}
		return c;
	}
}
