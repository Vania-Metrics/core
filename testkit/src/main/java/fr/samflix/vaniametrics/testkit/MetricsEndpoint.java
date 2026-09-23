package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Predicate;

/** The plugin's HTTP endpoint, seen from the test: scrape now, or wait until a condition holds. */
public final class MetricsEndpoint {

	private final String base;
	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	public MetricsEndpoint(String host, int port) {
		this.base = "http://" + host + ":" + port;
	}

	/** The HTTP status of a path, or -1 when nothing answers. */
	public int status(String path) {
		try {
			return get(path).statusCode();
		} catch (IOException e) {
			return -1;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		}
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
				.timeout(Duration.ofSeconds(15))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	/** One scrape; fails unless the answer is a 200. */
	public Scrape scrape() {
		try {
			HttpResponse<String> r = get("/metrics");
			if (r.statusCode() != 200) {
				throw new AssertionError("GET /metrics answered " + r.statusCode() + ": " + r.body());
			}
			return Scrape.parse(r.body());
		} catch (IOException e) {
			throw new AssertionError("GET " + base + "/metrics failed: " + e, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError("interrupted", e);
		}
	}

	/**
	 * Scrapes every half second until the condition holds.
	 *
	 * @param what said in the failure message: "the bot is counted online"
	 * @return the scrape that satisfied it
	 */
	public Scrape await(String what, Duration timeout, Predicate<Scrape> condition) {
		long deadline = System.nanoTime() + timeout.toNanos();
		Scrape last = null;
		while (true) {
			try {
				last = scrape();
				if (condition.test(last)) {
					return last;
				}
			} catch (AssertionError e) {
				if (System.nanoTime() > deadline) {
					throw e;
				}
			}
			if (System.nanoTime() > deadline) {
				throw new AssertionError("timed out after " + timeout.toSeconds() + " s waiting until " + what
						+ (last == null ? "" : "\nlast scrape:\n" + last.text()));
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted", e);
			}
		}
	}

	/** Waits until a path answers 200: the HTTP server is up. */
	public void awaitStatus(String path, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		int last = -1;
		while (System.nanoTime() < deadline) {
			last = status(path);
			if (last == 200) {
				return;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		throw new AssertionError("GET " + base + path + " never answered 200 (last: " + last + ")");
	}
}
