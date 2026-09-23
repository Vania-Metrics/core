package fr.samflix.vaniametrics.core.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A proxy whose backends answer, or not, as told. */
public final class FakeProxyServer implements ProxyServer {

	public final List<ProxyPlayer> players = new ArrayList<>();
	public final List<Backend> backends = new ArrayList<>();
	/** Advertised slots per backend; a backend missing from this map does not answer. */
	public final Map<String, Integer> answers = new HashMap<>();

	@Override
	public int playerCount() {
		return players.size();
	}

	@Override
	public List<ProxyPlayer> players() {
		return List.copyOf(players);
	}

	@Override
	public List<Backend> backends() {
		return List.copyOf(backends);
	}

	@Override
	public int ping(String backend, long timeoutSeconds) throws Exception {
		Integer slots = answers.get(backend);
		if (slots == null) {
			throw new java.net.ConnectException("connection refused: " + backend);
		}
		return slots;
	}
}
