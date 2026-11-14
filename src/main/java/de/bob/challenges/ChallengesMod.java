package de.bob.challenges;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class ChallengesMod implements ModInitializer {
	public static final String MOD_ID = "challenges";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		boolean worldWasReset = WorldReset.runPendingReset();
		ChallengeManager manager = ChallengeManager.get();
		manager.load(worldWasReset);

		ChallengeCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(manager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> manager.onServerStopping());
		ServerTickEvents.END_SERVER_TICK.register(manager::tick);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> manager.allowPlayerDamage(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register(manager::onEntityDeath);

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer serverPlayer) || manager.allowBlockBreak(serverPlayer));

		ServerPlayerEvents.JOIN.register(manager::onPlayerReady);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> manager.onPlayerReady(newPlayer));

		LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> manager.modifyDrops(holder, drops));

		LOGGER.info("Challenges-Mod geladen{}", worldWasReset ? " (neue Welt)" : "");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
