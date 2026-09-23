package fr.samflix.vaniametrics.testkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

/** One answer of {@code /metrics}, parsed: families from the {@code # TYPE} lines, and samples. */
public final class Scrape {

	/** One line: a series and its value. */
	public record Sample(String name, Map<String, String> labels, double value) {

		/** Whether every given label pair ({@code "k1", "v1", "k2", "v2"}) matches. */
		public boolean has(String... labelPairs) {
			for (int i = 0; i < labelPairs.length; i += 2) {
				if (!labelPairs[i + 1].equals(labels.get(labelPairs[i]))) {
					return false;
				}
			}
			return true;
		}
	}

	private final String text;
	private final Map<String, String> types = new LinkedHashMap<>();
	private final List<Sample> samples = new ArrayList<>();

	private Scrape(String text) {
		this.text = text;
	}

	public static Scrape parse(String text) {
		Scrape s = new Scrape(text);
		for (String line : text.lines().toList()) {
			if (line.startsWith("# TYPE ")) {
				String[] parts = line.split(" ");
				s.types.put(parts[2], parts[3]);
			} else if (!line.isBlank() && !line.startsWith("#")) {
				s.samples.add(sample(line));
			}
		}
		return s;
	}

	private static Sample sample(String line) {
		int brace = line.indexOf('{');
		int space;
		String name;
		Map<String, String> labels = new LinkedHashMap<>();
		if (brace < 0) {
			space = line.indexOf(' ');
			name = line.substring(0, space);
		} else {
			name = line.substring(0, brace);
			int i = brace + 1;
			while (line.charAt(i) != '}') {
				int eq = line.indexOf('=', i);
				String key = line.substring(i, eq);
				StringBuilder value = new StringBuilder();
				int j = eq + 2;
				while (line.charAt(j) != '"') {
					char c = line.charAt(j);
					if (c == '\\') {
						char next = line.charAt(j + 1);
						value.append(next == 'n' ? '\n' : next);
						j += 2;
					} else {
						value.append(c);
						j++;
					}
				}
				labels.put(key, value.toString());
				i = j + 1;
				if (line.charAt(i) == ',') {
					i++;
				}
			}
			space = i + 1;
		}
		String raw = line.substring(space).trim();
		// A timestamp may follow the value; the plugin writes none, but stay tolerant.
		int sp = raw.indexOf(' ');
		return new Sample(name, Collections.unmodifiableMap(labels), number(sp < 0 ? raw : raw.substring(0, sp)));
	}

	private static double number(String s) {
		return switch (s) {
			case "+Inf" -> Double.POSITIVE_INFINITY;
			case "-Inf" -> Double.NEGATIVE_INFINITY;
			default -> Double.parseDouble(s);
		};
	}

	/** The raw text, for failure messages. */
	public String text() {
		return text;
	}

	public Set<String> families() {
		return new TreeSet<>(types.keySet());
	}

	public boolean has(String family) {
		return types.containsKey(family);
	}

	/** Samples with this exact name ({@code _bucket}, {@code _count}... included). */
	public List<Sample> samples(String name) {
		return samples.stream().filter(s -> s.name().equals(name)).toList();
	}

	/** The first sample with this name whose labels include the given pairs. */
	public OptionalDouble value(String name, String... labelPairs) {
		return samples.stream()
				.filter(s -> s.name().equals(name) && s.has(labelPairs))
				.mapToDouble(Sample::value)
				.findFirst();
	}

	/** The sum over every sample with this name whose labels include the given pairs; 0 if none. */
	public double sum(String name, String... labelPairs) {
		return samples.stream()
				.filter(s -> s.name().equals(name) && s.has(labelPairs))
				.mapToDouble(Sample::value)
				.sum();
	}

	/** The lines about one family, to quote in a failure message. */
	public String excerpt(String family) {
		StringBuilder out = new StringBuilder();
		text.lines().filter(l -> l.contains(family)).limit(20).forEach(l -> out.append(l).append('\n'));
		return out.isEmpty() ? "(no line mentions " + family + ")" : out.toString();
	}
}
