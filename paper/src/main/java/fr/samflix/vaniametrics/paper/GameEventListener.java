package fr.samflix.vaniametrics.paper;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Game events, counted as they happen.
 *
 * <p>Counters, not gauges. A death is an event, not a state: the useful question is "how many per
 * hour", which Grafana answers with {@code rate()} on a counter.
 *
 * <p>{@code EventPriority.MONITOR} and {@code ignoreCancelled} throughout: this only observes.
 * MONITOR runs last, so the event is seen as other plugins left it, and an event cancelled by a
 * protection plugin is not counted; otherwise we would measure attempts, not outcomes.
 *
 * <p>Cardinality trap: {@code mc_server_commands_total{command="..."}} with the command as typed
 * would create one series per typo; a player typing {@code /qsdfgh} creates a series that lives
 * forever. The labelled commands are therefore a fixed list, and everything else is {@code other}.
 */
final class GameEventListener implements Listener {

	private static final Set<String> TRACKED_COMMANDS =
			Set.of("spawn", "tp", "home", "warp", "balance", "pay", "help", "msg");

	private final Counter connections;
	private final Counter deaths;
	private final Counter mobDeaths;
	private final Counter blocks;
	private final Counter commands;
	private final Counter messages;
	private final Counter crafts;
	private final Histogram session;

	private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

	GameEventListener(MetricRegistry r) {
		connections = r.counter("server_connections_total",
				"Joins and quits. result = join|quit.", "result");
		deaths = r.counter("server_deaths_total", "Player deaths, by cause.", "cause");
		mobDeaths = r.counter("server_mob_deaths_total",
				"Mobs killed. Only kills BY A PLAYER count: a skeleton burning in the sun says "
						+ "nothing about server activity.",
				"entity_type");
		blocks = r.counter("server_blocks_total", "Blocks broken and placed. action = break|place.", "action");
		commands = r.counter("server_commands_total",
				"Commands run. The label is limited to a fixed list of commands, everything else "
						+ "is \"other\", to bound cardinality.",
				"command");
		messages = r.counter("server_chat_messages_total", "Chat messages.");
		crafts = r.counter("server_items_crafted_total", "Items crafted.");
		session = r.histogram("server_session_seconds",
				"Session length, measured on quit.", Histogram.SESSION_SECONDS);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent e) {
		connections.inc("join");
		sessionStart.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		connections.inc("quit");
		Long start = sessionStart.remove(e.getPlayer().getUniqueId());
		if (start != null) {
			session.observe((System.currentTimeMillis() - start) / 1000.0);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent e) {
		var cause = e.getEntity().getLastDamageCause();
		deaths.inc(cause == null ? "unknown" : cause.getCause().name().toLowerCase(Locale.ROOT));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDeath(EntityDeathEvent e) {
		if (e.getEntity().getKiller() != null) {
			mobDeaths.inc(e.getEntityType().name().toLowerCase(Locale.ROOT));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		blocks.inc("break");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		blocks.inc("place");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCraft(CraftItemEvent e) {
		crafts.inc();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		String raw = e.getMessage();
		int space = raw.indexOf(' ');
		String name = (space < 0 ? raw.substring(1) : raw.substring(1, space))
				.toLowerCase(Locale.ROOT);
		commands.inc(TRACKED_COMMANDS.contains(name) ? name : "other");
	}

	/**
	 * Uses {@code AsyncPlayerChatEvent} rather than Paper's Adventure chat event: the former
	 * exists on every branch, the latter only on recent ones. A message counter is not worth
	 * tying to an API that keeps moving.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	@SuppressWarnings("deprecation")
	public void onChat(AsyncPlayerChatEvent e) {
		messages.inc();
	}
}
