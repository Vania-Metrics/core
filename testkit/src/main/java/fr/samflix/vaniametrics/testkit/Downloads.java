package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Third-party jars (the plugins a collector reads from), downloaded once and checked.
 *
 * <p>Every download is pinned by SHA-512, the digest Modrinth publishes. A jar that does not
 * match is refused before any container starts: a test must never run against a file nobody
 * chose.
 */
public final class Downloads {

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(20))
			.build();

	private Downloads() {}

	/**
	 * The jar at this URL, from the cache when already there.
	 *
	 * @return a file named as the URL's last segment, decoded ({@code %2B} becomes {@code +}):
	 *     some plugins are referred to by file name, Nova's {@code -javaagent} for one
	 */
	public static Path fetch(String url, String sha512) {
		String expected = sha512.trim().toLowerCase(Locale.ROOT);
		if (!expected.matches("[0-9a-f]{128}")) {
			throw new IllegalArgumentException("not a SHA-512: " + sha512 + " (for " + url + ")");
		}
		String name = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
		Path dir = Harness.cache().resolve("plugins").resolve(expected.substring(0, 16));
		Path file = dir.resolve(name);
		try {
			if (Files.exists(file) && expected.equals(sha512(file))) {
				return file;
			}
			Files.createDirectories(dir);
			Path tmp = Files.createTempFile(dir, name, ".part");
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofMinutes(5))
					.header("User-Agent", "Vania-Metrics/testkit (https://github.com/Vania-Metrics)")
					.build();
			HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("GET " + url + " answered " + response.statusCode());
			}
			try (InputStream in = response.body()) {
				Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
			}
			String actual = sha512(tmp);
			if (!expected.equals(actual)) {
				Files.deleteIfExists(tmp);
				throw new IllegalStateException("SHA-512 mismatch for " + url + "\n  expected " + expected
						+ "\n  got      " + actual);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return file;
		} catch (IOException e) {
			throw new UncheckedIOException("downloading " + url, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while downloading " + url, e);
		}
	}

	static String sha512(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			try (InputStream in = Files.newInputStream(file)) {
				byte[] buffer = new byte[64 * 1024];
				for (int n; (n = in.read(buffer)) > 0; ) {
					digest.update(buffer, 0, n);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
