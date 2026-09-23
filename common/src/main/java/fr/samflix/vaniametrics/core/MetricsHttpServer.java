package fr.samflix.vaniametrics.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Platform;

/**
 * The scrape endpoint: {@code GET /metrics}.
 *
 * <p>{@code com.sun.net.httpserver} has been in the JDK since Java 6 and is plenty for serving a
 * text document to one client every fifteen seconds. Bundling Jetty or Netty would add megabytes
 * and risk class conflicts with the server, which ships its own.
 *
 * <p>It has its own executor, separate from the server thread and the scheduler: a slow scrape
 * must not delay a tick or a background task.
 */
final class MetricsHttpServer {

	private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";
	private static final String PLAIN_TEXT = "text/plain; charset=utf-8";

	private final Platform platform;
	private final Config config;
	private final Supplier<String> scrape;

	private HttpServer server;

	MetricsHttpServer(Platform platform, Config config, Supplier<String> scrape) {
		this.platform = platform;
		this.config = config;
		this.scrape = scrape;
	}

	void start() throws IOException {
		String bind = config.getString("http.bind", "0.0.0.0");
		int port = config.getInt("http.port", 9940);
		String path = config.getString("http.path", "/metrics");
		String token = config.getString("http.token", "");

		server = HttpServer.create(new InetSocketAddress(bind, port), 4);
		server.createContext(path, e -> handle(e, token));
		// A free liveness endpoint, for a Kubernetes probe.
		server.createContext("/healthz", e -> write(e, 200, "ok\n", PLAIN_TEXT));
		server.setExecutor(Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "vania-metrics-http");
			t.setDaemon(true);
			return t;
		}));
		server.start();
		platform.info("serving metrics on http://" + bind + ":" + port + path
				+ (token.isEmpty() ? "" : " (token required)"));
	}

	void stop() {
		if (server != null) {
			// No grace period: an in-flight scrape is lost, and Prometheus retries on the next
			// interval.
			server.stop(0);
		}
	}

	private void handle(HttpExchange e, String token) throws IOException {
		try {
			if (!"GET".equals(e.getRequestMethod())) {
				write(e, 405, "method not allowed\n", PLAIN_TEXT);
				return;
			}
			if (!token.isEmpty()) {
				String header = e.getRequestHeaders().getFirst("Authorization");
				if (header == null || !header.equals("Bearer " + token)) {
					write(e, 401, "missing or invalid token\n", PLAIN_TEXT);
					return;
				}
			}
			write(e, 200, scrape.get(), CONTENT_TYPE);
		} catch (Exception error) {
			platform.error("answering scrape", error);
			// A 500 rather than an empty page: Prometheus must see a failure, otherwise it records
			// the absence of metrics as if the server had nothing to report.
			write(e, 500, "collection failed\n", PLAIN_TEXT);
		}
	}

	private void write(HttpExchange e, int status, String body, String contentType)
			throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		e.getResponseHeaders().set("Content-Type", contentType);
		e.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = e.getResponseBody()) {
			out.write(bytes);
		}
	}
}
