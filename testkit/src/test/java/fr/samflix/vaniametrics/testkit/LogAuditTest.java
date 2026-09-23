package fr.samflix.vaniametrics.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** The log lines below were copied from real servers, one per loader format. */
class LogAuditTest {

	private static List<String> audit(String log) {
		return LogAudit.findings(log, List.of(Pattern.compile(Pattern.quote(LogAudit.STARTUP_TIMEOUT))));
	}

	@Test
	void aCleanStartIsClean() {
		String log = """
				[15:24:29 INFO]: [VaniaMetrics] Loading server plugin VaniaMetrics v0.3.0
				[15:24:29 WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
				[15:24:32 INFO]: [VaniaMetrics] collector jvm: on scrape
				[15:53:57 INFO] [vaniametrics]: serving metrics on http://0.0.0.0:9940/metrics
				""";
		assertEquals(List.of(), audit(log));
	}

	@Test
	void aWarningFromACollectorIsAFinding() {
		String log = "[15:24:32 WARN]: [VaniaMetrics] Chunky: service not found, pregen will not be measured\n";
		assertEquals(1, audit(log).size());
	}

	@Test
	void warningsAreFoundInEveryLoaderFormat() {
		for (String line : List.of(
				"[15:21:58] [Server thread/WARN]: [VaniaMetrics] something",
				"[15:46:38] [Server thread/\u001B[33mWARN\u001B[m] [vaniametrics]: something",
				"[15:53:57 ERROR] [vaniametrics]: something",
				"16:00:10 [WARNING] [VaniaMetrics] something",
				"[16:15:58 \u001B[m\u001B[33;1mWARN\u001B[m\u001B[m] [vaniametrics] something",
				"[15:24:32 WARN]: [VaniaMetrics-Chunky] something")) {
			assertEquals(1, audit(line + "\n").size(), line);
		}
	}

	@Test
	void anAllowedWarningIsNotAFinding() {
		String log = "[15:24:30 WARN]: [VaniaMetrics] collector tick: " + LogAudit.STARTUP_TIMEOUT
				+ "; expected while the server is still starting\n";
		assertEquals(List.of(), audit(log));
	}

	@Test
	void aStackTraceThroughOurCodeIsAFinding() {
		String log = """
				[15:30:00 ERROR]: Could not pass event PlayerJoinEvent to VaniaMetrics v0.4.0
				java.lang.NullPointerException: boom
					at fr.samflix.vaniametrics.bukkit.BukkitEvents.onJoin(BukkitEvents.java:42)
					at org.bukkit.plugin.java.JavaPluginLoader$1.execute(JavaPluginLoader.java:1)
				Caused by: java.lang.IllegalStateException
					... 3 more
				[15:30:01 INFO]: next line
				""";
		List<String> findings = audit(log);
		assertEquals(1, findings.size(), findings.toString());
		assertTrue(findings.get(0).contains("NullPointerException"), findings.get(0));
	}

	@Test
	void someoneElsesStackTraceIsNotOurs() {
		String log = """
				[15:30:00 ERROR]: Error occurred while enabling SomePlugin
				java.lang.RuntimeException: not us
					at com.example.SomePlugin.onEnable(SomePlugin.java:10)
				""";
		assertEquals(List.of(), audit(log));
	}
}
