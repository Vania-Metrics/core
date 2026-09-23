package fr.samflix.vaniametrics.testkit;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A server or proxy the plugin can run on, named as in {@code compatibility.yml}.
 *
 * <p>The family says which core jar it takes: every Bukkit fork takes {@code vania-metrics-bukkit},
 * BungeeCord and Waterfall take {@code vania-metrics-bungee}.
 */
public enum Loader {
	PAPER(Family.BUKKIT),
	PURPUR(Family.BUKKIT),
	FOLIA(Family.BUKKIT),
	SPIGOT(Family.BUKKIT),
	BUKKIT(Family.BUKKIT),
	SPONGE(Family.SPONGE),
	VELOCITY(Family.VELOCITY),
	BUNGEECORD(Family.BUNGEE),
	WATERFALL(Family.BUNGEE),
	GEYSER(Family.GEYSER);

	/** The jar a loader takes, and what the tests do with it. */
	public enum Family {
		BUKKIT, SPONGE, VELOCITY, BUNGEE, GEYSER;

		/** The core jar's name prefix: {@code vania-metrics-bukkit}. */
		public String coreJar() {
			return "vania-metrics-" + name().toLowerCase(Locale.ROOT);
		}

		/** A proxy has players but no world, and needs a backend to send them to. */
		public boolean isProxy() {
			return this == VELOCITY || this == BUNGEE || this == GEYSER;
		}
	}

	public final Family family;

	Loader(Family family) {
		this.family = family;
	}

	/** The key in {@code compatibility.yml}. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<Loader> byKey(String key) {
		return Arrays.stream(values()).filter(l -> l.key().equals(key)).findFirst();
	}
}
