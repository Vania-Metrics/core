package fr.samflix.vaniametrics.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VaniaMetricsProviderTest {

	@AfterEach
	void reset() {
		VaniaMetricsProvider.set(null);
	}

	@Test
	void getFailsWithAHintWhileTheCoreIsNotLoaded() {
		IllegalStateException e = assertThrows(IllegalStateException.class, VaniaMetricsProvider::get);
		assertTrue(e.getMessage().contains("depend on VaniaMetrics"), e.getMessage());
		assertTrue(VaniaMetricsProvider.find().isEmpty());
	}
}
