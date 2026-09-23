package fr.samflix.vaniametrics.core.game;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Game events, counted as they happen. Each loader forwards its own events here.
 *
 * <p>Counters, not gauges. A death is an event, not a state: the useful question is "how many per
 * hour", which Grafana answers with {@code rate()} on a counter.
 *
 * <p>Loaders should forward events as the server finally decided them (Bukkit's MONITOR priority,
 * cancelled events ignored): we measure outcomes, not attempts.
 *
 * <p>Cardinality trap: {@code mc_server_commands_total{command="..."}} with the command as typed
 * would create one series per typo. The labelled commands are therefore a fixed list, and
 * everything else is {@code other}.
 */
public final class GameEvents {

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

	public GameEvents(MetricRegistry r) {
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

	public void join(UUID player) {
		connections.inc("join");
		sessionStart.put(player, System.currentTimeMillis());
	}

	public void quit(UUID player) {
		connections.inc("quit");
		Long start = sessionStart.remove(player);
		if (start != null) {
			session.observe((System.currentTimeMillis() - start) / 1000.0);
		}
	}

	/** @param cause the damage cause, or {@code null} when unknown */
	public void playerDeath(String cause) {
		deaths.inc(cause == null ? "unknown" : cause.toLowerCase(Locale.ROOT));
	}

	/** Only for mobs killed by a player. */
	public void mobKilledByPlayer(String entityType) {
		mobDeaths.inc(entityType.toLowerCase(Locale.ROOT));
	}

	public void blockBroken() {
		blocks.inc("break");
	}

	public void blockPlaced() {
		blocks.inc("place");
	}

	public void itemCrafted() {
		crafts.inc();
	}

	public void chatMessage() {
		messages.inc();
	}

	/** @param commandLine the command as typed, with or without the leading slash */
	public void command(String commandLine) {
		String raw = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
		int space = raw.indexOf(' ');
		String name = (space < 0 ? raw : raw.substring(0, space)).toLowerCase(Locale.ROOT);
		commands.inc(TRACKED_COMMANDS.contains(name) ? name : "other");
	}
}
