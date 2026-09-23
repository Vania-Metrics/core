package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Spigot and CraftBukkit server jars, built from source.
 *
 * <p>Neither is downloadable: SpigotMC only distributes BuildTools, and the itzg image refuses to
 * build CraftBukkit past 1.14. So BuildTools runs here once, on the machine running the tests,
 * and both jars are kept in the cache: minutes the first time, nothing after. The revision is a
 * Spigot build number, which pins every repository BuildTools checks out.
 */
public final class BuildTools {

	/** BuildTools itself, build 201. */
	private static final String URL = "https://hub.spigotmc.org/jenkins/job/BuildTools/201/artifact/target/BuildTools.jar";
	private static final String SHA512 = "f12e5a99b199dec121f0ff3870093ee0695290156c0c5929d54b4ec861840589"
			+ "f2f860257e424b2dcfce6581ec9ec8849fa0b1783382fa0d84a7d57d090b34d5";
	/** Spigot build 4598: Minecraft 1.21.11. */
	public static final String REVISION = "4598";

	private BuildTools() {}

	/**
	 * The server jar, building both on first use.
	 *
	 * @param kind {@code spigot} or {@code craftbukkit}
	 */
	public static synchronized Path jar(String kind) {
		Path dir = Harness.cache().resolve("buildtools").resolve(REVISION);
		Path jar = dir.resolve(kind + "-" + REVISION + ".jar");
		if (Files.exists(jar)) {
			return jar;
		}
		try {
			Files.createDirectories(dir);
			// Another test JVM may be building the same revision: wait for it instead of racing.
			try (FileChannel channel = FileChannel.open(dir.resolve(".lock"),
					StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					FileLock lock = channel.lock()) {
				if (!Files.exists(jar)) {
					build(dir);
				}
				if (!lock.isValid()) {
					throw new IllegalStateException("lost the lock on " + dir);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (!Files.exists(jar)) {
			throw new IllegalStateException("BuildTools did not produce " + jar);
		}
		return jar;
	}

	/** Builds both jars at once: they share the long part (decompiling, patching). */
	private static void build(Path dir) throws IOException {
		Path tools = Downloads.fetch(URL, SHA512);
		Path work = dir.resolve("work");
		Path out = dir.resolve("out");
		Files.createDirectories(work);
		Files.createDirectories(out);
		Path log = Harness.reports().resolve("logs").resolve("buildtools-" + REVISION + ".log");
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		System.out.println("Building Spigot and CraftBukkit " + REVISION + " with BuildTools (minutes, once)...");
		Process p = new ProcessBuilder(java, "-Xmx1536m", "-jar", tools.toString(),
				"--rev", REVISION, "--compile", "craftbukkit,spigot", "--output-dir", out.toString(), "--nogui")
				.directory(work.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
		try {
			if (!p.waitFor(30, TimeUnit.MINUTES)) {
				p.destroyForcibly();
				throw new IllegalStateException("BuildTools did not finish within 30 minutes; see " + log);
			}
		} catch (InterruptedException e) {
			p.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while BuildTools ran", e);
		}
		if (p.exitValue() != 0) {
			throw new IllegalStateException("BuildTools failed with " + p.exitValue() + "; see " + log);
		}
		for (String kind : List.of("spigot", "craftbukkit")) {
			try (Stream<Path> files = Files.list(out)) {
				Path built = files.filter(f -> f.getFileName().toString().startsWith(kind + "-"))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("BuildTools produced no " + kind + " jar; see " + log));
				Files.move(built, dir.resolve(kind + "-" + REVISION + ".jar"), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		// The work tree (sources, a Maven repository) weighs a gigabyte; only the jars are kept.
		delete(work);
		delete(out);
	}

	private static void delete(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(p);
			}
		}
	}
}
