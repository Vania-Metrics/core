package fr.samflix.vaniametrics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.samflix.vaniametrics.api.Config;

/** Lives in common because the default metrics.properties does. */
class ConfigLoadTest {

	@TempDir
	Path dataDirectory;

	@Test
	void theDefaultFileIsWrittenOnFirstStartThenRead() throws Exception {
		FakePlatform platform = new FakePlatform(dataDirectory.resolve("VaniaMetrics"));
		Config c = Config.load(platform, Map.of());
		Path file = platform.dataDirectory().resolve("metrics.properties");
		assertTrue(Files.exists(file));
		assertTrue(Files.readString(file).contains("http.port"));
		assertEquals(9940, c.getInt("http.port", -1));
	}

	@Test
	void anEditedFileIsKeptAndTheEnvironmentStillWins() throws Exception {
		Files.writeString(dataDirectory.resolve("metrics.properties"), "http.port = 9950\nhttp.path = /m\n");
		FakePlatform platform = new FakePlatform(dataDirectory);
		Config c = Config.load(platform, Map.of("VANIA_METRICS_HTTP_PORT", "9960"));
		assertEquals(9960, c.getInt("http.port", -1));
		assertEquals("/m", c.getString("http.path", ""));
		assertEquals("http.port = 9950\nhttp.path = /m\n",
				Files.readString(dataDirectory.resolve("metrics.properties")));
	}
}
