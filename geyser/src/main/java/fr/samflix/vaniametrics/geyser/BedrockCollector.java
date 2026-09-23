package fr.samflix.vaniametrics.geyser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * What only Bedrock players have: the device they play on, how they control it, and whether their
 * account is linked to a Java one. Every label is a small enum, so cardinality stays bounded.
 */
final class BedrockCollector implements Collector {

	private final GeyserApi api;

	private Gauge byDevice;
	private Gauge byInput;
	private Gauge linked;

	BedrockCollector(GeyserApi api) {
		this.api = api;
	}

	@Override
	public String name() {
		return "bedrock";
	}

	@Override
	public void declare(MetricRegistry r) {
		byDevice = r.gauge("bedrock_players_by_device",
				"Bedrock players by device (android, ios, windows, xbox, ps4, nx...).", "device");
		byInput = r.gauge("bedrock_players_by_input",
				"Bedrock players by input mode (keyboard_mouse, touch, controller, vr).", "input");
		linked = r.gauge("bedrock_players_linked",
				"Bedrock players whose account is linked to a Java account.");
	}

	@Override
	public void collect(MetricRegistry r) {
		byDevice.clear();
		byInput.clear();
		Map<String, Integer> devices = new HashMap<>();
		Map<String, Integer> inputs = new HashMap<>();
		int linkedCount = 0;
		for (GeyserConnection c : api.onlineConnections()) {
			devices.merge(label(c.platform()), 1, Integer::sum);
			inputs.merge(label(c.inputMode()), 1, Integer::sum);
			if (c.isLinked()) {
				linkedCount++;
			}
		}
		devices.forEach((d, n) -> byDevice.set(n, d));
		inputs.forEach((i, n) -> byInput.set(n, i));
		linked.set(linkedCount);
	}

	private static String label(Enum<?> value) {
		return value == null ? "unknown" : value.name().toLowerCase(Locale.ROOT);
	}
}
