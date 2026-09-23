package fr.samflix.vaniametrics.testkit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the Gradle task hands to the tests, as system properties.
 *
 * <ul>
 *   <li>{@code vania.it.jars}: the plugin jars under test (core, and the collector's own);
 *   <li>{@code vania.it.projectDir}: the repository, where {@code compatibility.yml} lives;
 *   <li>{@code vania.it.status}: {@code tested} runs the cells that must pass, {@code untested}
 *       the others;
 *   <li>{@code vania.it.platforms}: a comma-separated filter, empty for all;
 *   <li>{@code vania.it.all}: also run the cells the manifest says do not work;
 *   <li>{@code vania.it.reports}: where results and logs go.
 * </ul>
 */
public final class Harness {

	private Harness() {}

	public static List<Path> jars() {
		String value = System.getProperty("vania.it.jars", "");
		return Arrays.stream(value.split(File.pathSeparator))
				.filter(s -> !s.isBlank())
				.map(Path::of)
				.toList();
	}

	/** The jar whose file name starts with this prefix followed by a version. */
	public static Path jar(String prefix) {
		return jars().stream()
				.filter(p -> p.getFileName().toString().matches(java.util.regex.Pattern.quote(prefix) + "-\\d.*\\.jar"))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no " + prefix + " jar among vania.it.jars "
						+ jars() + "; is the Gradle task wired to build it?"));
	}

	/** The version in a jar name: {@code vania-metrics-bukkit-0.4.0.jar} gives 0.4.0. */
	public static String version(Path jar) {
		String name = jar.getFileName().toString();
		return name.replaceFirst("^.*?-(\\d[^-]*)\\.jar$", "$1");
	}

	public static Path projectDir() {
		return Path.of(System.getProperty("vania.it.projectDir", "."));
	}

	public static Path manifest() {
		return projectDir().resolve("compatibility.yml");
	}

	/** {@code tested} or {@code untested}. */
	public static String status() {
		return System.getProperty("vania.it.status", "tested");
	}

	/** The platforms asked for; empty means all. */
	public static Set<String> platforms() {
		return Arrays.stream(System.getProperty("vania.it.platforms", "").split(","))
				.map(s -> s.trim().toLowerCase(Locale.ROOT))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	public static boolean includeUnsupported() {
		return Boolean.getBoolean("vania.it.all");
	}

	public static Path reports() {
		Path dir = Path.of(System.getProperty("vania.it.reports", "build/vania-it"));
		try {
			Files.createDirectories(dir.resolve("logs"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		return dir;
	}

	/** Downloads and built server jars, kept between runs (and cached by CI). */
	public static Path cache() {
		String dir = System.getProperty("vania.it.cache", "");
		if (dir.isBlank()) {
			String env = System.getenv("VANIA_IT_CACHE");
			dir = env != null && !env.isBlank() ? env : System.getProperty("user.home") + "/.cache/vania-metrics";
		}
		return Path.of(dir);
	}
}
