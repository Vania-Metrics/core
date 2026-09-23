package fr.samflix.vaniametrics.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a server log for what must not be there.
 *
 * <p>Two findings:
 * <ul>
 *   <li>a stack trace that goes through our code ({@code fr.samflix}, {@code vaniametrics}),
 *       wherever it was logged from;
 *   <li>a warning or an error written by one of our loggers ({@code [VaniaMetrics]},
 *       {@code [VaniaMetrics-Chunky]}, {@code [vaniametrics]}...). A collector that could not find
 *       its target plugin only warns, and the plugin keeps running: without this rule the cell
 *       would pass while measuring nothing.
 * </ul>
 *
 * <p>Formats differ by loader (Paper, Spigot, Velocity, BungeeCord, Sponge, Geyser) and some are
 * coloured, so colours are stripped and the rules only look for the level word and our logger
 * name on the same line.
 */
public final class LogAudit {

	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");
	private static final Pattern FRAME = Pattern.compile("^\\s+at \\S|^\\s*Caused by: |^\\s+\\.\\.\\. \\d+ more");
	private static final Pattern OURS = Pattern.compile("(?i)fr\\.samflix|vaniametrics");
	private static final Pattern OUR_LOGGER = Pattern.compile("(?i)\\[vaniametrics[^\\]]*\\]");
	private static final Pattern WARN_OR_WORSE = Pattern.compile("\\b(WARN|WARNING|ERROR|SEVERE|FATAL)\\b");

	/** Expected while a server starts: a scrape that reached the main thread before it was free. */
	public static final String STARTUP_TIMEOUT = "main thread did not respond within 5 s";

	private LogAudit() {}

	/** The log without colour codes. */
	public static String clean(String log) {
		return ANSI.matcher(log).replaceAll("");
	}

	/**
	 * @param allowed regular expressions; a warning line matching one is not a finding
	 * @return one entry per finding, empty when the log is clean
	 */
	public static List<String> findings(String log, List<Pattern> allowed) {
		List<String> lines = clean(log).lines().toList();
		List<String> out = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (FRAME.matcher(line).find()) {
				// A stack trace: from the line that introduced it to its last frame.
				int start = i - 1;
				int end = i;
				while (end + 1 < lines.size() && FRAME.matcher(lines.get(end + 1)).find()) {
					end++;
				}
				List<String> trace = lines.subList(Math.max(0, start), end + 1);
				if (trace.stream().anyMatch(l -> OURS.matcher(l).find())) {
					out.add("stack trace through our code:\n  "
							+ String.join("\n  ", trace.subList(0, Math.min(trace.size(), 12))));
				}
				i = end;
			} else if (OUR_LOGGER.matcher(line).find() && WARN_OR_WORSE.matcher(line).find()
					&& allowed.stream().noneMatch(p -> p.matcher(line).find())) {
				out.add("warning from our logger: " + line.strip());
			}
		}
		return out;
	}
}
