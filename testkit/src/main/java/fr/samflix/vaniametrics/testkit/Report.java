package fr.samflix.vaniametrics.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.DynamicTest;
import org.opentest4j.TestAbortedException;

/**
 * The table every run ends with: one row per cell, what the manifest claims, what happened.
 *
 * <p>Written to {@code results-<status>.md} next to the logs; CI copies it into the run summary.
 */
public final class Report {

	/** One cell. {@code result} is passed, failed or skipped. */
	public record Row(String cell, String claim, String result, String build, String detail, long seconds) {
	}

	private static final List<Row> ROWS = new CopyOnWriteArrayList<>();

	private Report() {}

	/**
	 * A dynamic test that records its own outcome.
	 *
	 * @param claim what the manifest says: yes, untested
	 * @param build the server build, read once the cell ran (the log states it)
	 */
	public static DynamicTest cell(String name, String claim, Supplier<String> build, Cell body) {
		return DynamicTest.dynamicTest(name, () -> {
			long start = System.nanoTime();
			try {
				body.run();
				ROWS.add(new Row(name, claim, "passed", build.get(), "", seconds(start)));
			} catch (TestAbortedException e) {
				ROWS.add(new Row(name, claim, "skipped", "", e.getMessage(), seconds(start)));
				throw e;
			} catch (Throwable t) {
				ROWS.add(new Row(name, claim, "failed", build.get(), firstLine(t), seconds(start)));
				throw t;
			}
		});
	}

	/** A cell that is not run, with the reason; listed so the table covers every platform. */
	public static DynamicTest skipped(String name, String claim, String reason) {
		return DynamicTest.dynamicTest(name, () -> {
			ROWS.add(new Row(name, claim, "skipped", "", reason, 0));
			throw new TestAbortedException(reason);
		});
	}

	/** The body of a cell. */
	@FunctionalInterface
	public interface Cell {
		void run() throws Exception;
	}

	private static long seconds(long start) {
		return (System.nanoTime() - start) / 1_000_000_000L;
	}

	private static String firstLine(Throwable t) {
		String m = String.valueOf(t.getMessage());
		int nl = m.indexOf('\n');
		return (t.getClass().getSimpleName() + ": " + (nl < 0 ? m : m.substring(0, nl))).replace('|', '/');
	}

	/** Writes the table for this task's cells. */
	public static Path write(String title, String status) {
		List<String> out = new ArrayList<>();
		out.add("### " + title + " (" + status + ")");
		out.add("");
		out.add("| cell | claimed | result | build | seconds | detail |");
		out.add("|---|---|---|---|---|---|");
		for (Row r : ROWS) {
			String icon = switch (r.result()) {
				case "passed" -> "✅ passed";
				case "failed" -> "❌ failed";
				default -> "⏭️ skipped";
			};
			out.add("| " + r.cell() + " | " + r.claim() + " | " + icon + " | " + r.build() + " | "
					+ r.seconds() + " | " + r.detail() + " |");
		}
		out.add("");
		Path file = Harness.reports().resolve("results-" + status + ".md");
		try {
			Files.write(file, out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		ROWS.clear();
		return file;
	}
}
