package fr.samflix.vaniametrics.api;

/**
 * The version, and the only place it is written.
 *
 * <p>It lives in the API rather than the core because four things need it: the
 * {@code mc_build_info} label, Velocity's {@code @Plugin} annotation (which requires a compile-time
 * constant, hence {@code static final}), Bukkit's {@code plugin.yml}, and the jar names. The root
 * {@code build.gradle.kts} reads it from this file; nothing copies it.
 */
public final class Version {

	public static final String VALUE = "0.5.0";

	private Version() {}
}
