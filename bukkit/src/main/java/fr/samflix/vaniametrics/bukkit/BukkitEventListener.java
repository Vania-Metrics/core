package fr.samflix.vaniametrics.bukkit;

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

import fr.samflix.vaniametrics.core.game.GameEvents;

/**
 * Forwards Bukkit events to {@link GameEvents}. MONITOR and {@code ignoreCancelled} throughout:
 * the event is seen as other plugins left it, and a cancelled action is not counted.
 */
final class BukkitEventListener implements Listener {

	private final GameEvents events;

	BukkitEventListener(GameEvents events) {
		this.events = events;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		events.join(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		events.quit(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent e) {
		var cause = e.getEntity().getLastDamageCause();
		events.playerDeath(cause == null ? null : cause.getCause().name());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDeath(EntityDeathEvent e) {
		if (e.getEntity().getKiller() != null) {
			events.mobKilledByPlayer(e.getEntityType().name());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		events.blockBroken();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		events.blockPlaced();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCraft(CraftItemEvent e) {
		events.itemCrafted();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		events.command(e.getMessage());
	}

	/**
	 * {@code AsyncPlayerChatEvent} rather than Paper's Adventure chat event: it exists on every
	 * Bukkit implementation.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	@SuppressWarnings("deprecation")
	public void onChat(AsyncPlayerChatEvent e) {
		events.chatMessage();
	}
}
