package fr.samflix.vaniametrics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.core.game.FakeGameServer;
import fr.samflix.vaniametrics.core.game.GameEvents;
import fr.samflix.vaniametrics.core.game.PlayerCollector;
import fr.samflix.vaniametrics.core.game.TickCollector;
import fr.samflix.vaniametrics.core.game.WorldCollector;
import fr.samflix.vaniametrics.core.proxy.FakeProxyServer;
import fr.samflix.vaniametrics.core.proxy.ProxyCollector;
import fr.samflix.vaniametrics.core.proxy.ProxyEvents;
import fr.samflix.vaniametrics.core.proxy.ProxyServer;

/**
 * The metric contract: every family the core publishes, with its type and label names.
 *
 * <p>Dashboards and alerts depend on these names; a rename breaks them without an error anywhere.
 * Each profile is compared to a file under {@code src/test/resources/contract}. A deliberate change
 * is recorded with {@code ./gradlew :vania-metrics-common:test -Pvania.contract.update}, and the
 * diff is what gets reviewed.
 *
 * <p>Host metrics are left out: what the cgroup and disk collectors publish depends on the machine
 * running the tests, not on the code.
 */
class MetricContractTest {

	@TempDir
	Path dataDirectory;

	private Exporter exporter;

	@AfterEach
	void stop() {
		if (exporter != null) {
			exporter.stop();
		}
	}

	/** Always fails: the only way to make the error counter appear. */
	private static final class Failing implements Collector {
		@Override
		public String name() {
			return "failing";
		}

		@Override
		public void collect(MetricRegistry r) {
			throw new IllegalStateException("expected");
		}
	}

	private FakePlatform start(List<Collector> collectors, Config config) throws Exception {
		FakePlatform platform = new FakePlatform(dataDirectory);
		exporter = new Exporter(platform, config);
		exporter.start(collectors);
		return platform;
	}

	private static Config config() {
		Properties p = new Properties();
		p.setProperty("http.bind", "127.0.0.1");
		p.setProperty("http.port", "0");
		return Config.of(p, Map.of());
	}

	@ParameterizedTest
	@ValueSource(strings = {"paper", "spigot", "folia", "sponge"})
	void gameServer(String profile) throws Exception {
		FakeGameServer server = switch (profile) {
			case "paper" -> FakeGameServer.paper();
			case "spigot" -> FakeGameServer.spigot();
			case "folia" -> FakeGameServer.folia();
			case "sponge" -> FakeGameServer.sponge();
			default -> throw new IllegalArgumentException(profile);
		};
		server.withPlayer("probe");
		Config config = config();
		FakePlatform platform = start(List.of(new TickCollector(server),
				new WorldCollector(server, config), new PlayerCollector(server, config), new Failing()),
				config);

		GameEvents events = new GameEvents(exporter.registry());
		UUID probe = UUID.randomUUID();
		events.join(probe);
		events.quit(probe);
		events.playerDeath("lava");
		events.mobKilledByPlayer("zombie");
		events.blockBroken();
		events.blockPlaced();
		events.itemCrafted();
		events.chatMessage();
		events.command("/spawn");

		platform.runBackgroundTasks();
		exporter.scrape();
		check("game-" + profile, exporter.scrape());
	}

	@Test
	void proxy() throws Exception {
		FakeProxyServer proxy = new FakeProxyServer();
		proxy.players.add(new ProxyServer.ProxyPlayer(20, "vanilla"));
		proxy.backends.add(new ProxyServer.Backend("lobby", 1));
		proxy.backends.add(new ProxyServer.Backend("down", 0));
		proxy.answers.put("lobby", 50);
		Config config = config();
		FakePlatform platform = start(List.of(new ProxyCollector(proxy, config), new Failing()), config);

		ProxyEvents events = new ProxyEvents(exporter.registry());
		events.listPing();
		events.preLogin();
		events.login();
		events.serverSwitch(null, "lobby");
		events.kickedFrom("lobby");
		events.disconnect();

		platform.runBackgroundTasks();
		exporter.scrape();
		check("proxy", exporter.scrape());
	}

	private static void check(String profile, String rendered) throws IOException {
		Path dir = Path.of(System.getProperty("vania.contract.dir", "src/test/resources/contract"));
		Path file = dir.resolve(profile + ".txt");
		List<String> actual = contract(rendered);
		if (Boolean.getBoolean("vania.contract.update")) {
			Files.createDirectories(dir);
			List<String> out = new ArrayList<>(List.of(
					"# Metric contract, profile " + profile + ": family, type, label names.",
					"# Rewritten by: ./gradlew :vania-metrics-common:test -Pvania.contract.update",
					"# A line that disappears or changes breaks dashboards and alerts."));
			out.addAll(actual);
			Files.write(file, out);
			return;
		}
		List<String> expected = Files.exists(file)
				? Files.readAllLines(file).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList()
				: List.of();
		assertEquals(String.join("\n", expected), String.join("\n", actual),
				"The published metrics changed for profile " + profile + ". If this is deliberate, "
						+ "run with -Pvania.contract.update and review the diff of " + file);
	}

	/** One line per family: name, type, label names in publication order. */
	static List<String> contract(String rendered) {
		Map<String, String> types = new LinkedHashMap<>();
		Map<String, List<String>> labels = new LinkedHashMap<>();
		for (String line : rendered.lines().toList()) {
			if (line.startsWith("# TYPE ")) {
				String[] parts = line.split(" ");
				types.put(parts[2], parts[3]);
			} else if (!line.startsWith("#") && !line.isBlank()) {
				int brace = line.indexOf('{');
				int space = line.lastIndexOf(' ');
				String sample = brace >= 0 ? line.substring(0, brace) : line.substring(0, space);
				String family = familyOf(sample, types);
				List<String> keys = brace >= 0 ? labelNames(line.substring(brace + 1)) : List.of();
				if ("histogram".equals(types.get(family))) {
					keys = keys.stream().filter(k -> !k.equals("le")).toList();
				}
				labels.putIfAbsent(family, keys);
			}
		}
		List<String> out = new ArrayList<>();
		types.forEach((family, type) -> {
			if (!family.startsWith("mc_host_")) {
				out.add((family + " " + type + " " + String.join(",", labels.getOrDefault(family, List.of()))).trim());
			}
		});
		return out;
	}

	private static String familyOf(String sample, Map<String, String> types) {
		for (String suffix : List.of("_bucket", "_sum", "_count")) {
			if (sample.endsWith(suffix)) {
				String base = sample.substring(0, sample.length() - suffix.length());
				if ("histogram".equals(types.get(base))) {
					return base;
				}
			}
		}
		return sample;
	}

	/** Label names from {@code a="x",b="y\"z"} value..., honouring escaped quotes in values. */
	private static List<String> labelNames(String rest) {
		List<String> names = new ArrayList<>();
		int i = 0;
		while (i < rest.length() && rest.charAt(i) != '}') {
			int eq = rest.indexOf('=', i);
			names.add(rest.substring(i, eq));
			int j = eq + 2;
			while (rest.charAt(j) != '"') {
				j += rest.charAt(j) == '\\' ? 2 : 1;
			}
			i = j + 1;
			if (i < rest.length() && rest.charAt(i) == ',') {
				i++;
			}
		}
		return names;
	}
}
