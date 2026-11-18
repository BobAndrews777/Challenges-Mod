package de.bob.challenges;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.split.MobDamagePayload;
import de.bob.challenges.split.SplitManager;
import de.bob.challenges.split.SplitStatsPayload;
import de.bob.challenges.split.SplitSyncPayload;

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

		LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> {
			manager.modifyDrops(holder, drops);
			SplitManager.get().loot().tagLoot(holder, drops);
		});

		// Split-Challenge
		SplitManager split = SplitManager.get();
		PayloadTypeRegistry.clientboundPlay().register(SplitSyncPayload.TYPE, SplitSyncPayload.CODEC);
		ServerPlayerEvents.JOIN.register(player -> {
			if (player.level().getServer() != null) {
				split.onJoin(player.level().getServer(), player);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> split.checkWeaponUse(player));
		UseItemCallback.EVENT.register((player, level, hand) -> split.checkWeaponUse(player));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> split.checkToolUse(player));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> split.checkToolUse(player));
		EntitySleepEvents.SET_BED_OCCUPATION_STATE.register((entity, sleepingPos, bedState, occupied) -> split.skipBedOccupation(entity));
		EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> {
			Component problem = split.checkSameBed(player, sleepingPos);
			return problem == null ? null : new Player.BedSleepingProblem(problem);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register(split::onEntityRemoved);

		// Split-Challenge Etappe 2
		PayloadTypeRegistry.clientboundPlay().register(SplitStatsPayload.TYPE, SplitStatsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MobDamagePayload.TYPE, MobDamagePayload.CODEC);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> split.mobDamage().allowDamage(entity, source));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
				split.mobDamage().afterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> split.mobDamage().allowDeath(entity, source, amount));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> split.mobDamage().onRemoved(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				split.mobDamage().onRemoved(living);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> split.loot().onEntityLoad(entity));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> split.portals().onUseBlock(player, level, hitResult));

		LOGGER.info("Challenges-Mod geladen{}", worldWasReset ? " (neue Welt)" : "");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
