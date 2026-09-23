package fr.samflix.vaniametrics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ConfigTest {

	private static Properties file(String... keyValues) {
		Properties p = new Properties();
		for (int i = 0; i < keyValues.length; i += 2) {
			p.setProperty(keyValues[i], keyValues[i + 1]);
		}
		return p;
	}

	@Test
	void theFileIsReadWhenTheEnvironmentIsSilent() {
		Config c = Config.of(file("http.port", "9950"), Map.of());
		assertEquals(9950, c.getInt("http.port", 9940));
	}

	@Test
	void theEnvironmentWinsOverTheFile() {
		Config c = Config.of(file("http.port", "9950"), Map.of("VANIA_METRICS_HTTP_PORT", "9960"));
		assertEquals(9960, c.getInt("http.port", 9940));
	}

	@Test
	void dotsBecomeUnderscoresAndTheKeyIsUpperCased() {
		Config c = Config.of(new Properties(),
				Map.of("VANIA_METRICS_COLLECTOR_WORLD_INTERVAL", "2"));
		assertEquals(2, c.getSeconds("collector.world.interval", 15));
	}

	@Test
	void anEmptyEnvironmentValueIsIgnored() {
		Config c = Config.of(file("http.token", "secret"), Map.of("VANIA_METRICS_HTTP_TOKEN", ""));
		assertEquals("secret", c.getString("http.token", ""));
	}

	@Test
	void missingOrInvalidValuesFallBackToTheDefault() {
		Config c = Config.of(file("http.port", "not a number"), Map.of());
		assertEquals(9940, c.getInt("http.port", 9940));
		assertEquals("/metrics", c.getString("http.path", "/metrics"));
	}

	@Test
	void valuesAreTrimmed() {
		Config c = Config.of(file("http.path", "  /m  ", "http.port", " 1 "), Map.of());
		assertEquals("/m", c.getString("http.path", ""));
		assertEquals(1, c.getInt("http.port", 0));
	}

	@Test
	void collectorsAreOnUnlessSwitchedOff() {
		Config c = Config.of(file("collector.disk", "false"), Map.of());
		assertTrue(c.isCollectorEnabled("jvm", true));
		assertFalse(c.isCollectorEnabled("disk", true));
		assertFalse(c.isCollectorEnabled("packets", false));
	}
}
