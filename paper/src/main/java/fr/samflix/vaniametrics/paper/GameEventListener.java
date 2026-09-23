package fr.samflix.vaniametrics.paper;

import java.util.Locale;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * Ce qui se passe, compté au vol.
 *
 * <p>DES COMPTEURS ET NON DES JAUGES. Une mort est un événement, pas un état : ce qui intéresse
 * est « combien par heure », que Grafana obtient par {@code rate()} sur un compteur. Une jauge
 * « nombre de morts » n'aurait aucun sens.
 *
 * <p>{@code EventPriority.MONITOR} et {@code ignoreCancelled}, systématiquement : on OBSERVE, on
 * ne décide de rien. MONITOR est le dernier maillon, donc on voit l'événement tel que les autres
 * plugins l'ont laissé, et un événement annulé par une protection ne doit pas être compté — sinon
 * on mesurerait les tentatives et non les faits.
 *
 * <p>LE PIÈGE DE CARDINALITÉ EST ICI. {@code mc_commands_total{command="…"}} avec la commande
 * telle que tapée, c'est une série par faute de frappe : un joueur qui tape {@code /qsdfgh} crée
 * une série temporelle éternelle. La liste des commandes étiquetées est donc FIXÉE en
 * configuration, et tout le reste tombe dans {@code other}.
 */
final class GameEventListener implements Listener {

	private final Counter connexions;
	private final Counter morts;
	private final Counter mortsMobs;
	private final Counter blocs;
	private final Counter commandes;
	private final Counter messages;
	private final Counter fabrications;
	private final Histogram session;

	private final Set<String> commandesSuivies;
	private final java.util.Map<java.util.UUID, Long> debutSession =
			new java.util.concurrent.ConcurrentHashMap<>();

	GameEventListener(MetricRegistry r) {
		connexions = r.counter("server_connections_total",
				"Connexions et déconnexions. result = join|quit.", "result");
		morts = r.counter("server_deaths_total", "Morts de joueurs, par cause.", "cause");
		mortsMobs = r.counter("server_mob_deaths_total",
				"Mobs tués. Seuls ceux tués PAR UN JOUEUR sont comptés — un squelette qui brûle "
						+ "au soleil n'apprend rien sur l'activité du serveur.",
				"entity_type");
		blocs = r.counter("server_blocks_total", "Blocs cassés et posés. action = break|place.", "action");
		commandes = r.counter("server_commands_total",
				"Commandes exécutées. L'étiquette est bornée par la configuration : voir la note "
						+ "sur la cardinalité.",
				"command");
		messages = r.counter("server_chat_messages_total", "Messages de chat.");
		fabrications = r.counter("server_items_crafted_total", "Objets fabriqués.");
		session = r.histogram("server_session_seconds",
				"Durée des sessions, mesurée à la déconnexion.", Histogram.SECONDES_SESSION);
		commandesSuivies = Set.of("spawn", "tp", "home", "warp", "balance", "pay", "help", "msg");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent e) {
		connexions.inc("join");
		debutSession.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		connexions.inc("quit");
		Long debut = debutSession.remove(e.getPlayer().getUniqueId());
		if (debut != null) {
			session.observe((System.currentTimeMillis() - debut) / 1000.0);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent e) {
		var cause = e.getEntity().getLastDamageCause();
		morts.inc(cause == null ? "unknown" : cause.getCause().name().toLowerCase(Locale.ROOT));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDeath(EntityDeathEvent e) {
		if (e.getEntity().getKiller() != null) {
			mortsMobs.inc(e.getEntityType().name().toLowerCase(Locale.ROOT));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		blocs.inc("break");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		blocs.inc("place");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCraft(CraftItemEvent e) {
		fabrications.inc();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		String brut = e.getMessage();
		int espace = brut.indexOf(' ');
		String nom = (espace < 0 ? brut.substring(1) : brut.substring(1, espace))
				.toLowerCase(Locale.ROOT);
		commandes.inc(commandesSuivies.contains(nom) ? nom : "other");
	}

	/**
	 * Le chat.
	 *
	 * <p>Sur {@code AsyncPlayerChatEvent} et non sur l'événement Adventure de Paper : le premier
	 * existe sur toutes les branches, le second seulement sur les récentes. Un compteur de
	 * messages ne vaut pas de se lier à une API qui bouge.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	@SuppressWarnings("deprecation")
	public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
		messages.inc();
	}
}
