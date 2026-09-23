package fr.samflix.vaniametrics.api;

import java.util.Optional;

/**
 * Access point to the core from a collector plugin.
 *
 * <p>On Paper the core is also registered in Bukkit's {@code ServicesManager}, the idiomatic route.
 * This static provider exists because Velocity has no equivalent, and a collector written for both
 * platforms should not have to care.
 *
 * <p>Calling {@link #get()} is safe as long as the collector plugin depends on the core:
 * {@code depend: [VaniaMetrics]} in its plugin.yml, {@code dependencies} in its
 * velocity-plugin.json. Bukkit and Velocity then enable the core first. Without that declaration
 * nothing guarantees the order.
 */
public final class VaniaMetricsProvider {

	private static volatile VaniaMetrics instance;

	private VaniaMetricsProvider() {}

	/** The core, or an exception if it is not loaded yet. */
	public static VaniaMetrics get() {
		VaniaMetrics v = instance;
		if (v == null) {
			throw new IllegalStateException(
					"VaniaMetrics is not loaded. Does this plugin depend on VaniaMetrics?");
		}
		return v;
	}

	/** The core, if loaded. For code that would rather stay quiet than fail. */
	public static Optional<VaniaMetrics> find() {
		return Optional.ofNullable(instance);
	}

	/**
	 * Internal to the core. A collector calling this breaks every other collector.
	 *
	 * <p>Public only because the core lives in another package and another jar, out of reach of
	 * package-private access. LuckPerms and spark make the same trade-off for the same reason.
	 */
	public static void set(VaniaMetrics v) {
		instance = v;
	}
}
