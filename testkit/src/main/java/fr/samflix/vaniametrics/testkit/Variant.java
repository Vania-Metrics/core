package fr.samflix.vaniametrics.testkit;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One pinned server build to test a loader on.
 *
 * <p>Most loaders have one. Some have two: Velocity, because production still runs 3.5 while
 * the manifest vouches for 4.x; Sponge, because 1.21.11 only exists as a release candidate of an
 * API newer than the one the plugin compiles against.
 *
 * @param name shown in reports: {@code paper}, {@code velocity-3.5.1}
 * @param build the exact build, as the report should state it
 * @param exploratory never blocking, whatever the manifest says: a preview build
 * @param java the lowest Java the build runs on: 21 or 25
 * @param env container environment that selects the build
 * @param serverJar a server jar the image does not fetch itself, or null
 * @param files classpath resources copied into the container, by container path
 * @param ready the log line, as a regular expression, that says the server is up
 * @param minecraft the game version a client must speak to join
 */
public record Variant(Loader loader, String name, String build, boolean exploratory, int java,
		Map<String, String> env, ServerJar serverJar, Map<String, String> files, String ready, String minecraft) {

	/** A server jar provided by the harness: built by {@link BuildTools}, or downloaded and checked. */
	public record ServerJar(String fileName, Supplier<Path> source) {
	}

	/** ViaVersion 5.12.0, for the backend behind Geyser: accepts clients up to 26.3 on 1.21.11. */
	public static final CollectorTestConfig.Download VIAVERSION = new CollectorTestConfig.Download(
			"https://cdn.modrinth.com/data/P1OZGk5p/versions/FaishMnD/ViaVersion-5.12.0.jar",
			"2dfe562109179f08685dc84a66aedf7c810592223b5287b6355ba1e741d1737d"
					+ "015ca0c444b4bc01e5db2a4b10549d5028bf2e3f3337219ded709e44b579a4d8");

	/** Minecraft servers and Velocity. */
	private static final String DONE = "(?s).*Done \\(.*";
	/** BungeeCord and Waterfall print no "Done". */
	private static final String LISTENING = "(?s).*Listening on /.*";

	/** The pinned builds. Bump them here, deliberately. */
	public static List<Variant> of(Loader loader) {
		return switch (loader) {
			case PAPER -> List.of(itzg(loader, "paper", "Paper 1.21.11-132",
					Map.of("TYPE", "PAPER", "VERSION", "1.21.11", "PAPER_BUILD", "132")));
			case PURPUR -> List.of(itzg(loader, "purpur", "Purpur 1.21.11-2568",
					Map.of("TYPE", "PURPUR", "VERSION", "1.21.11", "PURPUR_BUILD", "2568")));
			case FOLIA -> List.of(itzg(loader, "folia", "Folia 1.21.11-14",
					Map.of("TYPE", "FOLIA", "VERSION", "1.21.11", "FOLIA_BUILD", "14")));
			case SPIGOT -> List.of(custom(loader, "spigot", "Spigot " + BuildTools.REVISION, false,
					new ServerJar("spigot.jar", () -> BuildTools.jar("spigot")), "1.21.11"));
			case BUKKIT -> List.of(custom(loader, "bukkit", "CraftBukkit " + BuildTools.REVISION, false,
					new ServerJar("craftbukkit.jar", () -> BuildTools.jar("craftbukkit")), "1.21.11"));
			case SPONGE -> List.of(
					custom(loader, "sponge", "SpongeVanilla 1.21.10-17.0.0", false,
							sponge("1.21.10-17.0.0", "cf949ff3cf3c4d7826aa32aebf39e58a375d5e562596dc79c2b7413447a64a1d"
									+ "49a7aeac1ddb686ffafe3009a6cd836f047e46532ab3c8e9d0319f7d5d27c51f"), "1.21.10"),
					// Minecraft 1.21.11 only exists on API 18, as a release candidate: the plugin is
					// built against API 17. Run to know, never blocking.
					custom(loader, "sponge-1.21.11-rc", "SpongeVanilla 1.21.11-18.0.0-RC2723", true,
							sponge("1.21.11-18.0.0-RC2723", "303b869e51d0de9b2c2fc430597c6e009c7968045fc1415c1591d114b54575d2"
									+ "cd41066d6196f37b41964b6684b090134ac584d31c52a1293b45dc7e61e64bc4"), "1.21.11"));
			case VELOCITY -> List.of(
					// What production runs; support ends 2026-09-30.
					proxy(loader, "velocity-3.5.1", "Velocity 3.5.1 #615", 21,
							Map.of("TYPE", "VELOCITY", "VELOCITY_VERSION", "3.5.1", "VELOCITY_BUILD_ID", "615"),
							"proxies/velocity-3.toml", "/config/velocity.toml", DONE),
					proxy(loader, "velocity-4.2.1", "Velocity 4.2.1 #32", 25,
							Map.of("TYPE", "VELOCITY", "VELOCITY_VERSION", "4.2.1-SNAPSHOT", "VELOCITY_BUILD_ID", "32"),
							"proxies/velocity-4.toml", "/config/velocity.toml", DONE));
			case BUNGEECORD -> List.of(proxy(loader, "bungeecord", "BungeeCord #2096", 21,
					Map.of("TYPE", "BUNGEECORD", "BUNGEE_JOB_ID", "2096"),
					"proxies/bungee.yml", "/config/config.yml", LISTENING));
			case WATERFALL -> List.of(proxy(loader, "waterfall", "Waterfall 1.21 #615", 21,
					Map.of("TYPE", "WATERFALL", "WATERFALL_VERSION", "1.21", "WATERFALL_BUILD_ID", "615"),
					"proxies/bungee.yml", "/config/config.yml", LISTENING));
			// Geyser 2.11 speaks Java 26.2 to its backend: the backend needs ViaVersion (see
			// VIAVERSION) to let it in. No image runs Geyser Standalone: a plain JRE does.
			case GEYSER -> List.of(new Variant(loader, "geyser", "Geyser Standalone 2.11.3 #1246", false, 21,
					Map.of(), new ServerJar("geyser.jar", () -> Downloads.fetch(
							"https://download.geysermc.org/v2/projects/geyser/versions/2.11.3/builds/1246/downloads/standalone",
							"44127e04eed9ecad62d6acdf029a4e05c955a64540629c177760c50240d19b36"
									+ "66bf1b4958ea8eaf8b5d56b7c4d2c8d2e2ee79bc042f0fb289c6116fa5094ea6")),
					Map.of("proxies/geyser.yml", "/data/config.yml"), DONE, "1.21.11"));
		};
	}

	private static Variant itzg(Loader loader, String name, String build, Map<String, String> env) {
		return new Variant(loader, name, build, false, 21, env, null, Map.of(), DONE, "1.21.11");
	}

	/** A server jar the image does not download: copied in, and run as-is. */
	private static Variant custom(Loader loader, String name, String build, boolean exploratory, ServerJar jar,
			String minecraft) {
		Map<String, String> env = new HashMap<>(Map.of(
				"TYPE", "CUSTOM",
				"CUSTOM_SERVER", "/server-jars/" + jar.fileName(),
				// CUSTOM does not sync /plugins unless told the server uses plugins.
				"USES_PLUGINS", "true"));
		if (loader == Loader.SPONGE) {
			// Sponge loads plugins from mods/, not plugins/.
			env.put("COPY_PLUGINS_DEST", "/data/mods");
		}
		return new Variant(loader, name, build, exploratory, 21, Map.copyOf(env), jar, Map.of(), DONE, minecraft);
	}

	private static Variant proxy(Loader loader, String name, String build, int java, Map<String, String> env,
			String config, String configPath, String ready) {
		return new Variant(loader, name, build, false, java, env, null, Map.of(config, configPath), ready, "1.21.11");
	}

	private static ServerJar sponge(String version, String sha512) {
		String url = "https://repo.spongepowered.org/repository/maven-releases/org/spongepowered/spongevanilla/"
				+ version + "/spongevanilla-" + version + "-universal.jar";
		return new ServerJar("sponge.jar", () -> Downloads.fetch(url, sha512));
	}
}
