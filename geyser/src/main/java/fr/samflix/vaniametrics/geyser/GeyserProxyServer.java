package fr.samflix.vaniametrics.geyser;

import java.util.ArrayList;
import java.util.List;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import fr.samflix.vaniametrics.core.proxy.ProxyServer;

/**
 * {@link ProxyServer} over the Geyser API. Geyser forwards to a single Java server and exposes no
 * way to ping it, so no backend is reported rather than a guessed status.
 */
final class GeyserProxyServer implements ProxyServer {

	private final GeyserApi api;

	GeyserProxyServer(GeyserApi api) {
		this.api = api;
	}

	@Override
	public int playerCount() {
		return api.onlineConnections().size();
	}

	@Override
	public List<ProxyPlayer> players() {
		List<ProxyPlayer> result = new ArrayList<>();
		for (GeyserConnection c : api.onlineConnections()) {
			// Bedrock clients send no brand; the device is published by BedrockCollector.
			result.add(new ProxyPlayer(c.ping(), null));
		}
		return result;
	}

	@Override
	public List<Backend> backends() {
		return List.of();
	}

	@Override
	public int ping(String backend, long timeoutSeconds) {
		throw new UnsupportedOperationException("Geyser reports no backends");
	}
}
