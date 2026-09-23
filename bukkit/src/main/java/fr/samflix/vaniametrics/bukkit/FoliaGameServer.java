package fr.samflix.vaniametrics.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import fr.samflix.vaniametrics.core.game.GameServer;
import fr.samflix.vaniametrics.core.game.PlayerSnapshot;
import fr.samflix.vaniametrics.core.game.TpsMeter;
import fr.samflix.vaniametrics.core.game.WorldSnapshot;

/**
 * {@link GameServer} for Folia. There is no main thread: each region ticks on its own thread, so
 * every read is dispatched to the thread that owns the data and awaited from the collector's
 * background thread.
 *
 * <ul>
 *   <li>Players are read on their own region, through their entity scheduler.
 *   <li>World-level state (time, weather) is read on the global region.
 *   <li>Entity, tile entity and chunk counts belong to many regions at once. Folia does not
 *       expose them globally; if the calls are refused they are left out rather than guessed.
 *   <li>TPS is the global region's, measured by {@link TpsMeter}. Tick durations are not
 *       published: there is no single tick to time.
 * </ul>
 */
final class FoliaGameServer implements GameServer {

	private static final long[] NO_DURATIONS = new long[0];
	private static final long TIMEOUT_SECONDS = 2;

	private final Plugin plugin;
	private final ServerFlavor flavor;
	private final TpsMeter tpsMeter;
	private volatile boolean worldCountersRefused;

	FoliaGameServer(Plugin plugin, ServerFlavor flavor, TpsMeter tpsMeter) {
		this.plugin = plugin;
		this.flavor = flavor;
		this.tpsMeter = tpsMeter;
	}

	@Override
	public boolean needsMainThread() {
		return false;
	}

	@Override
	public int maxPlayers() {
		return Bukkit.getMaxPlayers();
	}

	@Override
	public double[] tps() {
		return tpsMeter.tps();
	}

	@Override
	public double averageTickMillis() {
		return Double.NaN;
	}

	@Override
	public int currentTick() {
		return tpsMeter.ticks();
	}

	@Override
	public long[] recentTickDurations() {
		return NO_DURATIONS;
	}

	@Override
	public List<WorldSnapshot> worlds(boolean withEntityTypes) {
		return onGlobalRegion(() -> {
			List<WorldSnapshot> result = new ArrayList<>();
			for (World world : Bukkit.getWorlds()) {
				int entities = -1;
				int tileEntities = -1;
				int chunks = -1;
				if (flavor.worldCounters() && !worldCountersRefused) {
					try {
						entities = world.getEntityCount();
						tileEntities = world.getTileEntityCount();
						chunks = world.getChunkCount();
					} catch (UnsupportedOperationException | IllegalStateException e) {
						// Refused off the owning region. Asked once, then left out for good.
						worldCountersRefused = true;
						entities = tileEntities = chunks = -1;
						plugin.getLogger().info("world entity and chunk counts are not available on Folia");
					}
				}
				result.add(new WorldSnapshot(world.getName(), entities, tileEntities, chunks,
						world.getPlayers().size(), world.getFullTime(),
						BukkitGameServer.weather(world), Map.of()));
			}
			return result;
		});
	}

	@Override
	public List<PlayerSnapshot> players() {
		List<CompletableFuture<PlayerSnapshot>> pending = new ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			CompletableFuture<PlayerSnapshot> f = new CompletableFuture<>();
			// The retired callback fires if the player leaves before the task runs.
			p.getScheduler().run(plugin,
					task -> f.complete(BukkitGameServer.snapshot(p, flavor)),
					() -> f.complete(null));
			pending.add(f);
		}
		List<PlayerSnapshot> result = new ArrayList<>();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		for (CompletableFuture<PlayerSnapshot> f : pending) {
			try {
				// One shared deadline: a stalled region costs its own players, not a timeout each.
				PlayerSnapshot s = f.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
				if (s != null) {
					result.add(s);
				}
			} catch (Exception e) {
				// Region stalled or player gone: skip this player for this scrape.
			}
		}
		return result;
	}

	private <T> T onGlobalRegion(Supplier<T> read) {
		CompletableFuture<T> f = new CompletableFuture<>();
		Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
			try {
				f.complete(read.get());
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
		});
		try {
			return f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("global region did not answer in " + TIMEOUT_SECONDS + " s", e);
		}
	}
}
