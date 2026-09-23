package fr.samflix.vaniametrics.sponge;

import org.spongepowered.api.block.transaction.BlockTransaction;
import org.spongepowered.api.block.transaction.Operations;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.entity.damage.source.DamageSource;
import org.spongepowered.api.event.command.ExecuteCommandEvent;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.item.inventory.CraftItemEvent;
import org.spongepowered.api.event.message.PlayerChatEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.registry.RegistryTypes;

import fr.samflix.vaniametrics.core.game.GameEvents;

/**
 * Forwards Sponge events to {@link GameEvents}. {@code Order.POST}: the event is seen as other
 * plugins left it; Sponge skips cancelled events by default, so cancelled actions are not counted.
 * Only player actions count, as on Bukkit.
 */
final class SpongeEventListener {

	private final GameEvents events;

	SpongeEventListener(GameEvents events) {
		this.events = events;
	}

	@Listener(order = Order.POST)
	public void onJoin(ServerSideConnectionEvent.Join e) {
		events.join(e.player().uniqueId());
	}

	@Listener(order = Order.POST)
	public void onDisconnect(ServerSideConnectionEvent.Disconnect e) {
		// No profile means the client left before authenticating: it never joined either.
		e.profile().ifPresent(profile -> events.quit(profile.uuid()));
	}

	@Listener(order = Order.POST)
	public void onDeath(DestructEntityEvent.Death e) {
		if (e.entity() instanceof ServerPlayer) {
			events.playerDeath(e.cause().first(DamageSource.class)
					.map(s -> s.type().key(RegistryTypes.DAMAGE_TYPE).value())
					.orElse(null));
		} else if (e.cause().first(ServerPlayer.class).isPresent()) {
			events.mobKilledByPlayer(e.entity().type().key(RegistryTypes.ENTITY_TYPE).value());
		}
	}

	@Listener(order = Order.POST)
	public void onBlocks(ChangeBlockEvent.All e) {
		if (!(e.cause().root() instanceof ServerPlayer)) {
			return;
		}
		for (BlockTransaction t : e.transactions()) {
			if (t.operation().equals(Operations.BREAK.get())) {
				events.blockBroken();
			} else if (t.operation().equals(Operations.PLACE.get())) {
				events.blockPlaced();
			}
		}
	}

	@Listener(order = Order.POST)
	public void onCraft(CraftItemEvent.Craft e) {
		events.itemCrafted();
	}

	@Listener(order = Order.POST)
	public void onChat(PlayerChatEvent.Submit e) {
		events.chatMessage();
	}

	@Listener(order = Order.POST)
	public void onCommand(ExecuteCommandEvent.Pre e) {
		if (e.cause().root() instanceof ServerPlayer) {
			events.command(e.command());
		}
	}
}
