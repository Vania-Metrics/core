package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A collector's {@code collector-test.yml}: what its test server needs besides the core.
 *
 * <pre>
 * collectors: [pregen]     # names in mc_exporter_collector_info, not the repository name
 * runtime: java21          # java21 | java25: the game server's Java
 * memory: 3G               # the game server's heap, when 1G is not enough (Nova)
 * startup-timeout: 20m     # how long the server may take to start, when 8 minutes is not enough
 * jvm-args: []             # extra JVM flags for the game server
 * env: {}                  # extra environment, e.g. VANIA_METRICS_COLLECTOR_PLACEHOLDER_LIST
 * plugins:                 # per family: bukkit | velocity | bungee; the first is the target
 *   bukkit:
 *     - { url: https://..., sha512: ... }
 * per-platform:            # a platform whose plugins differ from its family's
 *   paper: { plugins: [] }
 * allow-logs: []           # regular expressions exempted from the log audit
 * </pre>
 *
 * <p>Unknown keys are refused: a misspelt key would otherwise be ignored in silence, and the test
 * would run with less than it was told.
 */
public record CollectorTestConfig(
		List<String> collectors,
		String runtime,
		String memory,
		Duration startupTimeout,
		List<String> jvmArgs,
		Map<String, String> env,
		Map<String, List<Download>> plugins,
		Map<String, List<Download>> perPlatform,
		List<Pattern> allowLogs) {

	/** A jar to download, pinned by digest. */
	public record Download(String url, String sha512) {
	}

	private static final Set<String> KEYS =
			Set.of("collectors", "runtime", "memory", "startup-timeout", "jvm-args", "env", "plugins", "per-platform",
					"allow-logs");

	public static CollectorTestConfig load(Path file) throws IOException {
		Map<String, Object> root = Yamls.load(file);
		for (String key : root.keySet()) {
			if (!KEYS.contains(key)) {
				throw new IllegalArgumentException(file + ": unknown key '" + key + "', expected one of " + KEYS);
			}
		}
		List<String> collectors = strings(root.get("collectors"));
		if (collectors.isEmpty()) {
			throw new IllegalArgumentException(file + ": 'collectors' must name at least one collector");
		}
		String runtime = root.getOrDefault("runtime", "java21").toString();
		if (!runtime.equals("java21") && !runtime.equals("java25")) {
			throw new IllegalArgumentException(file + ": runtime must be java21 or java25, not " + runtime);
		}
		String memory = root.getOrDefault("memory", "").toString();
		if (!memory.isEmpty() && !memory.matches("\\d+[MG]")) {
			throw new IllegalArgumentException(file + ": memory must look like 1536M or 3G, not " + memory);
		}
		Duration startupTimeout = null;
		String timeout = root.getOrDefault("startup-timeout", "").toString();
		if (!timeout.isEmpty()) {
			if (!timeout.matches("\\d+m")) {
				throw new IllegalArgumentException(file + ": startup-timeout is in minutes, like 20m, not " + timeout);
			}
			startupTimeout = Duration.ofMinutes(Long.parseLong(timeout.substring(0, timeout.length() - 1)));
		}
		Map<String, String> env = new LinkedHashMap<>();
		if (root.get("env") instanceof Map<?, ?> m) {
			m.forEach((k, v) -> env.put(String.valueOf(k), String.valueOf(v)));
		}
		Map<String, List<Download>> plugins = new LinkedHashMap<>();
		if (root.get("plugins") instanceof Map<?, ?> m) {
			m.forEach((family, list) -> plugins.put(String.valueOf(family), downloads(file, list)));
		}
		Map<String, List<Download>> perPlatform = new LinkedHashMap<>();
		if (root.get("per-platform") instanceof Map<?, ?> m) {
			m.forEach((platform, value) -> {
				if (!(value instanceof Map<?, ?> fields) || !fields.containsKey("plugins")) {
					throw new IllegalArgumentException(file + ": per-platform." + platform + " needs a plugins list");
				}
				perPlatform.put(String.valueOf(platform), downloads(file, fields.get("plugins")));
			});
		}
		List<Pattern> allow = strings(root.get("allow-logs")).stream().map(Pattern::compile).toList();
		return new CollectorTestConfig(collectors, runtime, memory, startupTimeout, strings(root.get("jvm-args")),
				env, plugins, perPlatform, allow);
	}

	private static List<String> strings(Object o) {
		List<String> out = new ArrayList<>();
		if (o instanceof List<?> list) {
			list.forEach(v -> out.add(String.valueOf(v)));
		} else if (o != null && !String.valueOf(o).isBlank()) {
			out.add(String.valueOf(o));
		}
		return out;
	}

	private static List<Download> downloads(Path file, Object o) {
		List<Download> out = new ArrayList<>();
		if (o == null) {
			return out;
		}
		if (!(o instanceof List<?> list)) {
			throw new IllegalArgumentException(file + ": a plugin list must be a list, got " + o);
		}
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> m) || m.get("url") == null || m.get("sha512") == null) {
				throw new IllegalArgumentException(file + ": each plugin needs url and sha512, got " + item);
			}
			out.add(new Download(String.valueOf(m.get("url")), String.valueOf(m.get("sha512"))));
		}
		return out;
	}

	/** The third-party jars for a platform: its own list if it has one, else its family's. */
	public List<Download> pluginsFor(Loader loader) {
		List<Download> own = perPlatform.get(loader.key());
		if (own != null) {
			return own;
		}
		String family = switch (loader.family) {
			case BUKKIT -> "bukkit";
			case VELOCITY -> "velocity";
			case BUNGEE -> "bungee";
			case SPONGE -> "sponge";
			case GEYSER -> "geyser";
		};
		return plugins.getOrDefault(family, List.of());
	}
}
