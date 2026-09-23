package fr.samflix.vaniametrics.core.game;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fr.samflix.vaniametrics.core.game.PlayerSnapshot.Stat;

/**
 * A game server whose capabilities are set by hand.
 *
 * <p>The profiles mirror what each loader can provide (see the loader modules): they document the
 * capability matrix and let the collectors be checked against it without a server. What a real
 * loader actually publishes is checked by the integration tests.
 */
public final class FakeGameServer implements GameServer {

	public double[] tps;
	public double averageTickMillis = Double.NaN;
	public int currentTick = -1;
	public long[] recentTickDurations = new long[0];
	public int maxPlayers = 20;
	public boolean mainThread = true;
	public boolean worldCounts = true;
	public boolean entityTypes = true;
	public boolean brand = true;
	public final List<String> worldNames = new ArrayList<>(List.of("world"));
	public final List<PlayerSnapshot> players = new ArrayList<>();
	/** The argument of the last {@link #worlds(boolean)} call. */
	public boolean lastWorldsWithTypes;

	/** Paper and Purpur: everything, from the server's own tick accounting. */
	public static FakeGameServer paper() {
		FakeGameServer s = new FakeGameServer();
		s.tps = new double[] {20, 20, 19.9};
		s.averageTickMillis = 12.5;
		s.currentTick = 1200;
		s.recentTickDurations = new long[] {10_000_000, 12_000_000, 60_000_000};
		return s;
	}

	/** CraftBukkit and Spigot: TPS from our own meter, no tick durations, no client brand. */
	public static FakeGameServer spigot() {
		FakeGameServer s = new FakeGameServer();
		s.tps = new double[] {20, 20, 20};
		s.currentTick = 1200;
		s.brand = false;
		return s;
	}

	/** Folia: no main thread, TPS from the global region, no per-type entity breakdown. */
	public static FakeGameServer folia() {
		FakeGameServer s = new FakeGameServer();
		s.tps = new double[] {20, 20, 20};
		s.currentTick = 1200;
		s.mainThread = false;
		s.entityTypes = false;
		return s;
	}

	/** Sponge: an average tick time but no per-tick durations, no client brand. */
	public static FakeGameServer sponge() {
		FakeGameServer s = new FakeGameServer();
		s.tps = new double[] {20, 20, 20};
		s.averageTickMillis = 8.0;
		s.currentTick = 1200;
		s.brand = false;
		return s;
	}

	/** Adds one online player with every statistic set. */
	public FakeGameServer withPlayer(String name) {
		Map<Stat, Long> stats = new EnumMap<>(Stat.class);
		for (Stat st : Stat.values()) {
			stats.put(st, 40L);
		}
		players.add(new PlayerSnapshot(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),
				name, 35, brand ? "vanilla" : null, "en_us", stats));
		return this;
	}

	@Override
	public boolean needsMainThread() {
		return mainThread;
	}

	@Override
	public int maxPlayers() {
		return maxPlayers;
	}

	@Override
	public double[] tps() {
		return tps;
	}

	@Override
	public double averageTickMillis() {
		return averageTickMillis;
	}

	@Override
	public int currentTick() {
		return currentTick;
	}

	@Override
	public long[] recentTickDurations() {
		return recentTickDurations;
	}

	@Override
	public List<WorldSnapshot> worlds(boolean withEntityTypes) {
		lastWorldsWithTypes = withEntityTypes;
		List<WorldSnapshot> out = new ArrayList<>();
		for (String name : worldNames) {
			int count = worldCounts ? 10 : -1;
			Map<String, Integer> byType = withEntityTypes && entityTypes
					? Map.of("cow", 12, "bat", 2)
					: Map.of();
			out.add(new WorldSnapshot(name, count, count, count, players.size(), 6000, 0, byType));
		}
		return out;
	}

	@Override
	public List<PlayerSnapshot> players() {
		return List.copyOf(players);
	}
}
