package fr.samflix.vaniametrics.bukkit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.samflix.vaniametrics.core.game.GameServer;
import fr.samflix.vaniametrics.core.game.PlayerSnapshot.Stat;
import fr.samflix.vaniametrics.core.game.PlayerSnapshot;
import fr.samflix.vaniametrics.core.game.TpsMeter;
import fr.samflix.vaniametrics.core.game.WorldSnapshot;

/** {@link GameServer} over the Bukkit API, using Paper's extras only where the server has them. */
final class BukkitGameServer implements GameServer {

	private static final long[] NO_DURATIONS = new long[0];

	private final ServerFlavor flavor;
	private final TpsMeter tpsMeter;

	/** @param tpsMeter used when the server has no tick API; {@code null} otherwise */
	BukkitGameServer(ServerFlavor flavor, TpsMeter tpsMeter) {
		this.flavor = flavor;
		this.tpsMeter = tpsMeter;
	}

	@Override
	public int maxPlayers() {
		return Bukkit.getMaxPlayers();
	}

	@Override
	public double[] tps() {
		return flavor.tickApi() ? Bukkit.getTPS() : tpsMeter.tps();
	}

	@Override
	public double averageTickMillis() {
		return flavor.tickApi() ? Bukkit.getAverageTickTime() : Double.NaN;
	}

	@Override
	public int currentTick() {
		return flavor.tickApi() ? Bukkit.getCurrentTick() : tpsMeter.ticks();
	}

	@Override
	public long[] recentTickDurations() {
		if (!flavor.tickApi()) {
			return NO_DURATIONS;
		}
		long[] durations = Bukkit.getTickTimes();
		return durations == null ? NO_DURATIONS : durations;
	}

	@Override
	public List<WorldSnapshot> worlds(boolean withEntityTypes) {
		List<WorldSnapshot> result = new ArrayList<>();
		for (World world : Bukkit.getWorlds()) {
			int entities;
			int tileEntities;
			int chunks;
			List<Entity> all = null;
			if (flavor.worldCounters()) {
				// O(1) counters the server already keeps.
				entities = world.getEntityCount();
				tileEntities = world.getTileEntityCount();
				chunks = world.getChunkCount();
			} else {
				// No counters: walk the loaded chunks. O(n), acceptable because this collector
				// runs in the background every 15 s, never on scrape.
				Chunk[] loaded = world.getLoadedChunks();
				chunks = loaded.length;
				int tiles = 0;
				for (Chunk c : loaded) {
					tiles += c.getTileEntities().length;
				}
				tileEntities = tiles;
				all = world.getEntities();
				entities = all.size();
			}

			Map<String, Integer> byType = Map.of();
			if (withEntityTypes) {
				byType = new HashMap<>();
				for (Entity e : all != null ? all : world.getEntities()) {
					byType.merge(e.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
				}
			}

			result.add(new WorldSnapshot(world.getName(), entities, tileEntities, chunks,
					world.getPlayers().size(), world.getFullTime(), weather(world), byType));
		}
		return result;
	}

	@Override
	public List<PlayerSnapshot> players() {
		List<PlayerSnapshot> result = new ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			result.add(snapshot(p, flavor));
		}
		return result;
	}

	/** Reads one player. Must run on the thread that owns the player. */
	static PlayerSnapshot snapshot(Player p, ServerFlavor flavor) {
		Map<Stat, Long> stats = new EnumMap<>(Stat.class);
		stats.put(Stat.PLAYER_KILLS, stat(p, Statistic.PLAYER_KILLS));
		stats.put(Stat.MOB_KILLS, stat(p, Statistic.MOB_KILLS));
		stats.put(Stat.DEATHS, stat(p, Statistic.DEATHS));
		stats.put(Stat.DAMAGE_DEALT, stat(p, Statistic.DAMAGE_DEALT));
		stats.put(Stat.DAMAGE_TAKEN, stat(p, Statistic.DAMAGE_TAKEN));
		stats.put(Stat.JUMPS, stat(p, Statistic.JUMP));
		// PLAY_ONE_MINUTE counts ticks despite its name.
		stats.put(Stat.PLAY_TICKS, stat(p, Statistic.PLAY_ONE_MINUTE));
		return new PlayerSnapshot(p.getUniqueId(), p.getName(), p.getPing(),
				flavor.clientBrand() ? p.getClientBrandName() : null, locale(p, flavor), stats);
	}

	@SuppressWarnings("deprecation")
	private static String locale(Player p, ServerFlavor flavor) {
		if (flavor.localeObject()) {
			Locale l = p.locale();
			return l == null ? null : l.toLanguageTag();
		}
		// Spigot only has the raw client string, "en_us"; turn it into a language tag.
		String raw = p.getLocale();
		return raw == null ? null : raw.replace('_', '-');
	}

	private static long stat(Player p, Statistic s) {
		try {
			return p.getStatistic(s);
		} catch (IllegalArgumentException e) {
			// A statistic removed by a Minecraft version. Zero rather than a failure.
			return 0;
		}
	}

	static int weather(World world) {
		if (world.isThundering()) {
			return 2;
		}
		return world.hasStorm() ? 1 : 0;
	}
}
