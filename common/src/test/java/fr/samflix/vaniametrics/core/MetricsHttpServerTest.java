package fr.samflix.vaniametrics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.samflix.vaniametrics.api.Config;

class MetricsHttpServerTest {

	@TempDir
	Path dataDirectory;

	private final HttpClient client = HttpClient.newHttpClient();
	private FakePlatform platform;
	private MetricsHttpServer server;

	private void start(Supplier<String> scrape, String... keyValues) throws Exception {
		Properties p = new Properties();
		p.setProperty("http.bind", "127.0.0.1");
		p.setProperty("http.port", "0");
		for (int i = 0; i < keyValues.length; i += 2) {
			p.setProperty(keyValues[i], keyValues[i + 1]);
		}
		platform = new FakePlatform(dataDirectory);
		server = new MetricsHttpServer(platform, Config.of(p, Map.of()), scrape);
		server.start();
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder get(String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
	}

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop();
		}
		client.close();
	}

	@Test
	void metricsAreServedAsPrometheusText() throws Exception {
		start(() -> "mc_exporter_up 1\n");
		HttpResponse<String> r = send(get("/metrics"));
		assertEquals(200, r.statusCode());
		assertEquals("mc_exporter_up 1\n", r.body());
		assertEquals("text/plain; version=0.0.4; charset=utf-8",
				r.headers().firstValue("Content-Type").orElseThrow());
	}

	@Test
	void healthzAnswersWithoutScraping() throws Exception {
		start(() -> {
			throw new AssertionError("healthz must not scrape");
		});
		HttpResponse<String> r = send(get("/healthz"));
		assertEquals(200, r.statusCode());
		assertEquals("ok\n", r.body());
	}

	@Test
	void thePathIsConfigurable() throws Exception {
		start(() -> "x 1\n", "http.path", "/custom");
		assertEquals(200, send(get("/custom")).statusCode());
		assertEquals(404, send(get("/metrics")).statusCode());
	}

	@Test
	void onlyGetIsAllowed() throws Exception {
		start(() -> "x 1\n");
		assertEquals(405, send(get("/metrics").POST(HttpRequest.BodyPublishers.noBody())).statusCode());
	}

	@Test
	void aTokenIsRequiredOnceConfigured() throws Exception {
		start(() -> "x 1\n", "http.token", "s3cret");
		assertEquals(401, send(get("/metrics")).statusCode());
		assertEquals(401, send(get("/metrics").header("Authorization", "Bearer wrong")).statusCode());
		assertEquals(200, send(get("/metrics").header("Authorization", "Bearer s3cret")).statusCode());
	}

	private static Set<Thread> httpThreads() {
		return Thread.getAllStackTraces().keySet().stream()
				.filter(t -> t.getName().equals("vania-metrics-http") && t.isAlive())
				.collect(Collectors.toSet());
	}

	@Test
	void stopReleasesTheHttpThreads() throws Exception {
		// A reload disables and re-enables the plugin: threads left behind pile up each time.
		Set<Thread> before = httpThreads();
		start(() -> "x 1\n");
		assertEquals(200, send(get("/metrics")).statusCode());
		server.stop();
		server = null;

		long deadline = System.nanoTime() + 5_000_000_000L;
		Set<Thread> left = httpThreads();
		left.removeAll(before);
		while (!left.isEmpty() && System.nanoTime() < deadline) {
			Thread.sleep(50);
			left = httpThreads();
			left.removeAll(before);
		}
		assertEquals(Set.of(), left);
	}

	@Test
	void aFailedScrapeIsA500AndIsLogged() throws Exception {
		start(() -> {
			throw new IllegalStateException("broken");
		});
		assertEquals(500, send(get("/metrics")).statusCode());
		assertFalse(platform.errors.isEmpty());
	}
}
