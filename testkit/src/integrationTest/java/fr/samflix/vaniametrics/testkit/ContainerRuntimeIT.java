package fr.samflix.vaniametrics.testkit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

/**
 * Checks the container runtime before any server does: when this fails, the platform cells would
 * all fail for a reason that has nothing to do with the plugin.
 */
class ContainerRuntimeIT {

	@Test
	void aContainerRunsAndItsOutputIsRead() {
		Assumptions.assumeTrue("tested".equals(System.getProperty("vania.it.status")),
				"checked once, with the cells that must pass");
		try (GenericContainer<?> c = new GenericContainer<>(Images.ALPINE)
				.withCommand("echo", "vania-metrics")
				.withStartupCheckStrategy(new OneShotStartupCheckStrategy())) {
			c.start();
			assertTrue(c.getLogs().contains("vania-metrics"), c.getLogs());
		}
	}
}
