package fr.samflix.vaniametrics.bukkit;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Which Bukkit implementation we run on, and which optional APIs it has.
 *
 * <p>Capabilities are probed by method presence rather than inferred from the server name: forks
 * add and remove APIs independently, and a probe cannot be wrong about what it finds. The code is
 * compiled against paper-api, so every Paper-only call must sit behind one of these flags, or it
 * throws {@code NoSuchMethodError} on Spigot.
 *
 * @param name lowercase implementation name: craftbukkit, spigot, paper, purpur...
 * @param tickApi Paper's TPS, average tick time and tick duration buffer
 * @param worldCounters Paper's O(1) entity, tile entity and chunk counters
 */
record ServerFlavor(
		String name,
		boolean tickApi,
		boolean worldCounters,
		boolean clientBrand,
		boolean localeObject) {

	static ServerFlavor detect() {
		String name = Bukkit.getName().toLowerCase(Locale.ROOT);
		if (name.equals("craftbukkit") && classExists("org.spigotmc.SpigotConfig")) {
			name = "spigot";
		}
		Class<?> server = Bukkit.getServer().getClass();
		return new ServerFlavor(
				name,
				methodExists(server, "getTickTimes") && methodExists(server, "getTPS"),
				methodExists(World.class, "getEntityCount")
						&& methodExists(World.class, "getTileEntityCount")
						&& methodExists(World.class, "getChunkCount"),
				methodExists(Player.class, "getClientBrandName"),
				methodExists(Player.class, "locale"));
	}

	private static boolean classExists(String name) {
		try {
			Class.forName(name, false, ServerFlavor.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static boolean methodExists(Class<?> type, String name) {
		for (var m : type.getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == 0) {
				return true;
			}
		}
		return false;
	}
}
