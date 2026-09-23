package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A repository's {@code compatibility.yml}: which platforms it claims, and how firmly.
 *
 * <p>Two shapes. The core lists the jar per platform with {@code status: tested|untested}; a
 * collector says whether its target plugin runs there ({@code plugin:}) and whether the collector
 * does ({@code collector: yes|untested|no}). Both reduce to one expectation per platform.
 */
public final class Manifest {

	/** How firmly a platform is claimed. */
	public enum Claim {
		/** Must work: a failure fails the build. */
		YES,
		/** Expected to work, never checked: run and reported, not fatal. */
		UNTESTED,
		/** Known not to work, or nothing to run: skipped with the reason. */
		NO
	}

	/** One platform line. {@code plugin} is null for the core. */
	public record Entry(String platform, Claim claim, String plugin, String note) {
	}

	private final Path file;
	private final boolean core;
	private final Map<String, Object> root;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	private Manifest(Path file, Map<String, Object> root) {
		this.file = file;
		this.root = root;
		this.core = root.containsKey("version") && !root.containsKey("plugin");
		Object platforms = root.get("platforms");
		if (!(platforms instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException(file + ": no platforms map");
		}
		map.forEach((k, v) -> entries.put(String.valueOf(k), entry(String.valueOf(k), v)));
	}

	/** Reads a manifest. Every value is read as written: see {@link Yamls#load}. */
	public static Manifest load(Path file) throws IOException {
		return new Manifest(file, Yamls.load(file));
	}

	private Entry entry(String platform, Object value) {
		if (!(value instanceof Map<?, ?> fields)) {
			throw new IllegalArgumentException(file + ": platform " + platform + " is not a map");
		}
		String note = string(fields.get("note"));
		if (core) {
			String status = string(fields.get("status"));
			Claim claim = switch (status) {
				case "tested" -> Claim.YES;
				case "untested" -> Claim.UNTESTED;
				default -> throw new IllegalArgumentException(
						file + ": " + platform + " has status '" + status + "', expected tested|untested");
			};
			return new Entry(platform, claim, null, note);
		}
		String plugin = string(fields.get("plugin"));
		String collector = string(fields.get("collector"));
		Claim claim = switch (collector) {
			case "yes" -> Claim.YES;
			case "untested" -> Claim.UNTESTED;
			case "no" -> Claim.NO;
			default -> throw new IllegalArgumentException(
					file + ": " + platform + " has collector '" + collector + "', expected yes|untested|no");
		};
		return new Entry(platform, claim, plugin, note);
	}

	private static String string(Object o) {
		return o == null ? "" : String.valueOf(o).trim().toLowerCase(Locale.ROOT);
	}

	public Path file() {
		return file;
	}

	/** Whether this is the core's manifest rather than a collector's. */
	public boolean isCore() {
		return core;
	}

	/** Platform lines, in file order. */
	public Map<String, Entry> entries() {
		return entries;
	}

	/** A top-level value as written ({@code minecraft}, {@code version}, {@code core}). */
	public String get(String key) {
		Object v = root.get(key);
		return v == null ? "" : String.valueOf(v).trim();
	}

	/** A value of the {@code plugin:} block of a collector manifest, or "". */
	public String plugin(String key) {
		return root.get("plugin") instanceof Map<?, ?> p && p.get(key) != null
				? String.valueOf(p.get(key)).trim()
				: "";
	}
}
