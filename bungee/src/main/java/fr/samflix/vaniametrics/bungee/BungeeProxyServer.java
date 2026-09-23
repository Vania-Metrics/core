package fr.samflix.vaniametrics.bungee;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import fr.samflix.vaniametrics.core.proxy.ProxyServer;

/** {@link ProxyServer} over the BungeeCord API. */
final class BungeeProxyServer implements ProxyServer {

	private final net.md_5.bungee.api.ProxyServer proxy;

	BungeeProxyServer(net.md_5.bungee.api.ProxyServer proxy) {
		this.proxy = proxy;
	}

	@Override
	public int playerCount() {
		return proxy.getOnlineCount();
	}

	@Override
	public List<ProxyPlayer> players() {
		List<ProxyPlayer> result = new ArrayList<>();
		for (ProxiedPlayer p : proxy.getPlayers()) {
			// BungeeCord's API exposes no client brand.
			result.add(new ProxyPlayer(p.getPing(), null));
		}
		return result;
	}

	@Override
	public List<Backend> backends() {
		List<Backend> result = new ArrayList<>();
		for (ServerInfo s : proxy.getServers().values()) {
			result.add(new Backend(s.getName(), s.getPlayers().size()));
		}
		return result;
	}

	@Override
	public int ping(String backend, long timeoutSeconds) throws Exception {
		ServerInfo server = proxy.getServerInfo(backend);
		if (server == null) {
			throw new IllegalArgumentException("unknown backend " + backend);
		}
		CompletableFuture<ServerPing> answer = new CompletableFuture<>();
		server.ping((ping, error) -> {
			if (error != null) {
				answer.completeExceptionally(error);
			} else {
				answer.complete(ping);
			}
		});
		ServerPing ping = answer.get(timeoutSeconds, TimeUnit.SECONDS);
		return ping.getPlayers() == null ? -1 : ping.getPlayers().getMax();
	}
}
