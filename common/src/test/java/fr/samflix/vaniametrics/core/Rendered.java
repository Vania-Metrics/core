package fr.samflix.vaniametrics.core;

import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

/** Reads what {@code MetricRegistry.render()} produced, the way a test wants to ask about it. */
public final class Rendered {

	private Rendered() {}

	/** Family names, from the {@code # TYPE} lines. */
	public static Set<String> families(String text) {
		Set<String> out = new TreeSet<>();
		text.lines().filter(l -> l.startsWith("# TYPE ")).forEach(l -> out.add(l.split(" ")[2]));
		return out;
	}

	/**
	 * The value of one series.
	 *
	 * @param series the sample as written, name and labels: {@code mc_server_tps{window="1m"}}
	 */
	public static OptionalDouble value(String text, String series) {
		return text.lines()
				.filter(l -> l.startsWith(series + " "))
				.mapToDouble(l -> number(l.substring(series.length() + 1)))
				.findFirst();
	}

	/** Prometheus spells infinities its own way; Java would not parse them. */
	private static double number(String s) {
		return switch (s) {
			case "+Inf" -> Double.POSITIVE_INFINITY;
			case "-Inf" -> Double.NEGATIVE_INFINITY;
			default -> Double.parseDouble(s);
		};
	}
}
