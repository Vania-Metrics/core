package fr.samflix.vaniametrics.api;

import java.util.UUID;

/**
 * A player as it appears in a metric: name and UUID.
 *
 * <p>Both, because they answer different questions. The name is what you read on a dashboard and
 * type in a filter. The UUID does not change: a player who renames stays the same player, and only
 * the UUID links their history across the rename.
 *
 * <pre>
 *   mc_quest_player_tags{player="Thesam1798"}                          readable, broken by a rename
 *   mc_quest_player_tags{uuid="f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2"}   stable, unreadable
 * </pre>
 *
 * <p>Carrying both labels costs nothing: they map one to one, so they add no series. The only case
 * where the series count grows is a rename: the old series stops, a new one starts, and the UUID
 * links them. That is intended.
 *
 * <p>The label order is fixed, {@code player} then {@code uuid}, for every collector. Prometheus
 * does not care, but a convention kept everywhere lets you copy a query from one dashboard to
 * another without re-reading it.
 *
 * <p>This type knows neither Bukkit nor Velocity; each platform builds it from its own player type.
 *
 * @param uuid the UUID, in canonical dashed form
 * @param name the player name
 */
public record PlayerRef(String uuid, String name) {

	public PlayerRef {
		uuid = uuid == null ? "" : uuid;
		name = name == null ? "" : name;
	}

	/**
	 * The factory to use everywhere: it fixes the UUID format.
	 *
	 * <p>{@code UUID.toString()} gives the dashed form. That is not a given (Mojang also exposes
	 * the compact, undashed form), and two collectors that picked different forms would publish
	 * two series for one player without anything flagging it.
	 */
	public static PlayerRef of(UUID uuid, String name) {
		return new PlayerRef(uuid == null ? "" : uuid.toString(), name);
	}

	/**
	 * The label values, in convention order.
	 *
	 * <p>Pass them as-is to instruments declared with {@code "player", "uuid"}. Going through this
	 * method rather than writing {@code (name, uuid)} by hand keeps the order identical across
	 * collectors; a swap would break nothing visible, it would just make dashboards wrong.
	 */
	public String[] labels() {
		return new String[] {name, uuid};
	}

	/** The same, followed by labels specific to the instrument. */
	public String[] labels(String... extra) {
		String[] all = new String[2 + extra.length];
		all[0] = name;
		all[1] = uuid;
		System.arraycopy(extra, 0, all, 2, extra.length);
		return all;
	}
}
