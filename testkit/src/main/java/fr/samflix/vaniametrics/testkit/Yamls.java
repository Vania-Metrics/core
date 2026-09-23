package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/** Reads the harness's YAML files. */
final class Yamls {

	private Yamls() {}

	/**
	 * A YAML map with every scalar kept as a string.
	 *
	 * <p>YAML 1.1, which SnakeYAML implements, turns {@code yes}, {@code no} and even the key
	 * {@code on} into booleans: {@code collector: no} would read as {@code false}, and {@code on:}
	 * as the key {@code true}.
	 */
	static Map<String, Object> load(Path file) throws IOException {
		LoaderOptions options = new LoaderOptions();
		Yaml yaml = new Yaml(new SafeConstructor(options), new Representer(new DumperOptions()),
				new DumperOptions(), options, new Resolver() {
					@Override
					protected void addImplicitResolvers() {
						// None: every scalar stays a string.
					}
				});
		try (Reader in = Files.newBufferedReader(file)) {
			Object doc = yaml.load(in);
			if (!(doc instanceof Map<?, ?> map)) {
				throw new IllegalArgumentException(file + ": not a YAML map");
			}
			Map<String, Object> root = new LinkedHashMap<>();
			map.forEach((k, v) -> root.put(String.valueOf(k), v));
			return root;
		}
	}
}
