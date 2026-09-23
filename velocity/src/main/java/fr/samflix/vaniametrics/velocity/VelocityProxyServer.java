package fr.samflix.vaniametrics.velocity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import fr.samflix.vaniametrics.core.proxy.ProxyServer;

/** {@link ProxyServer} over the Velocity API. */
final class VelocityProxyServer implements ProxyServer {

	private final com.velocitypowered.api.proxy.ProxyServer proxy;

	VelocityProxyServer(com.velocitypowered.api.proxy.ProxyServer proxy) {
		this.proxy = proxy;
	}

	@Override
	public int playerCount() {
		return proxy.getPlayerCount();
	}

	@Override
	public List<ProxyPlayer> players() {
		List<ProxyPlayer> result = new ArrayList<>();
		for (Player p : proxy.getAllPlayers()) {
			result.add(new ProxyPlayer((int) p.getPing(), p.getClientBrand()));
		}
		return result;
	}

	@Override
	public List<Backend> backends() {
		List<Backend> result = new ArrayList<>();
		for (RegisteredServer s : proxy.getAllServers()) {
			result.add(new Backend(s.getServerInfo().getName(), s.getPlayersConnected().size()));
		}
		return result;
	}

	@Override
	public int ping(String backend, long timeoutSeconds) throws Exception {
		RegisteredServer server = proxy.getServer(backend).orElseThrow();
		var response = server.ping().get(timeoutSeconds, TimeUnit.SECONDS);
		return response.getPlayers().map(p -> p.getMax()).orElse(-1);
	}
}
