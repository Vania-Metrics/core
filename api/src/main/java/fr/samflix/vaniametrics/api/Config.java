package fr.samflix.vaniametrics.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The configuration: a {@code metrics.properties} file that the environment can override.
 *
 * <p>Properties rather than YAML because the plugin runs on two platforms with different config
 * formats (Bukkit reads YAML through SnakeYAML, Velocity reads TOML through Configurate) and the
 * core must know neither. {@code java.util.Properties} is in the JDK, reads the same on both sides
 * and adds no dependency to relocate.
 *
 * <p>The environment wins over the file, as with Plan and LuckPerms: {@code http.port} is
 * overridden by {@code VANIA_METRICS_HTTP_PORT}. This lets a Helm chart or a compose file drive
 * everything without shipping a file.
 */
public final class Config {

	private static final String ENV_PREFIX = "VANIA_METRICS_";

	private final Properties props = new Properties();
	private final Map<String, String> env;

	private Config(Map<String, String> env) {
		this.env = env;
	}

	/**
	 * Loads the configuration, writing the default file first if it is missing.
	 *
	 * <p>The default file is commented, so an operator can tell what each key does without
	 * reading the code.
	 */
	public static Config load(Platform platform) {
		return load(platform, System.getenv());
	}

	/** {@link #load(Platform)} with a given environment instead of the process's. For tests. */
	public static Config load(Platform platform, Map<String, String> env) {
		Config c = new Config(env);
		Path file = platform.dataDirectory().resolve("metrics.properties");
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(platform.dataDirectory());
				try (InputStream in = Config.class.getResourceAsStream("/metrics.properties")) {
					if (in != null) {
						try (OutputStream out = Files.newOutputStream(file)) {
							in.transferTo(out);
						}
					}
				}
				platform.info("wrote default configuration: " + file);
			}
			if (Files.exists(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					c.props.load(in);
				}
			}
		} catch (IOException e) {
			platform.error("could not read the configuration, using defaults", e);
		}
		return c;
	}

	/** For tests, and for a platform without a data directory. */
	public static Config empty() {
		return new Config(System.getenv());
	}

	/**
	 * A configuration from given properties and environment, without touching the disk or the
	 * process environment. For tests.
	 */
	public static Config of(Properties properties, Map<String, String> env) {
		Config c = new Config(Map.copyOf(env));
		c.props.putAll(properties);
		return c;
	}

	private String raw(String key) {
		String value = env.get(ENV_PREFIX + key.replace('.', '_').toUpperCase(Locale.ROOT));
		return value != null && !value.isEmpty() ? value : props.getProperty(key);
	}

	public String getString(String key, String defaultValue) {
		String v = raw(key);
		return v == null ? defaultValue : v.trim();
	}

	public int getInt(String key, int defaultValue) {
		String v = raw(key);
		if (v == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public long getSeconds(String key, long defaultSeconds) {
		return getInt(key, (int) defaultSeconds);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String v = raw(key);
		return v == null ? defaultValue : Boolean.parseBoolean(v.trim());
	}

	/**
	 * Whether a collector is enabled ({@code collector.<name>}).
	 *
	 * <p>Everything is on by default except collectors known to be expensive, such as
	 * {@code packets} and {@code sql}, which must be asked for. An exporter that slows the server
	 * down out of the box does not get installed twice.
	 */
	public boolean isCollectorEnabled(String name, boolean defaultValue) {
		return getBoolean("collector." + name, defaultValue);
	}
}
