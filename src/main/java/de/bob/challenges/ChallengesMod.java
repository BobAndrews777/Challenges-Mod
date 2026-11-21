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
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.mobs.AbilityPayload;
import de.bob.challenges.mobs.DimsPayload;
import de.bob.challenges.mobs.MenuBook;
import de.bob.challenges.mobs.MotionPayload;
import de.bob.challenges.mobs.MobChallenge;
import de.bob.challenges.mobs.MobStatePayload;
import de.bob.challenges.split.MobDamagePayload;
import de.bob.challenges.split.MovementPayload;
import de.bob.challenges.split.SkinPayload;
import de.bob.challenges.split.SlotInfoPayload;
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
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MobChallenge.get().disguises().removeAll(server);
			manager.onServerStopping();
		});
		ServerTickEvents.END_SERVER_TICK.register(manager::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> de.bob.challenges.army.ArmyBattle.get().tick(server));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> manager.allowPlayerDamage(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register(manager::onEntityDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> de.bob.challenges.army.ArmyBattle.get().onEntityDeath(entity, source));

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer serverPlayer) || manager.allowBlockBreak(serverPlayer));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> manager.onBlockBroken(level, player, pos, state));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> de.bob.challenges.army.ArmyBattle.get().onBlockBroken(level, player, pos, state));

		ServerPlayerEvents.JOIN.register(manager::onPlayerReady);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> manager.onPlayerReady(newPlayer));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> de.bob.challenges.army.ArmyBattle.get().onRespawn(newPlayer));

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

		// Split-Challenge Etappe 3: geteilte Slots
		PayloadTypeRegistry.clientboundPlay().register(SlotInfoPayload.TYPE, SlotInfoPayload.CODEC);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> split.checkHandUse(player));
		UseItemCallback.EVENT.register((player, level, hand) -> split.checkHandUse(player));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> split.checkHandUse(player));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> split.checkHandUse(player));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> split.onEntityLoad(entity));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> MobChallenge.get().disguises().onEntityLoad(entity));

		// Mob-Challenge
		MobChallenge mobs = MobChallenge.get();
		PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MobStatePayload.TYPE, MobStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> mobs.onAbility(player, payload.ability()));
		});
		UseItemCallback.EVENT.register((player, level, hand) -> MenuBook.onUse(player, hand));
		UseItemCallback.EVENT.register((player, level, hand) -> mobs.onUseItem(player, hand));
		PayloadTypeRegistry.clientboundPlay().register(MotionPayload.TYPE, MotionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(de.bob.challenges.mobs.ReelPayload.TYPE, de.bob.challenges.mobs.ReelPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(de.bob.challenges.mobs.DisguiseMapPayload.TYPE, de.bob.challenges.mobs.DisguiseMapPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DimsPayload.TYPE, DimsPayload.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
				mobs.abilities().onDamage(entity, source));
		ServerPlayerEvents.JOIN.register(MenuBook::giveIfMissing);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> mobs.onUseEntity(player, hand, entity));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> mobs.allowDamage(entity, source, amount));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> mobs.allowDeath(entity));
		ServerPlayerEvents.JOIN.register(player -> {
			if (player.level().getServer() != null) {
				mobs.onJoin(player.level().getServer(), player);
			}
		});

		// Split-Challenge Etappe 4: Bewegung und Abbauen
		PayloadTypeRegistry.clientboundPlay().register(MovementPayload.TYPE, MovementPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkinPayload.TYPE, SkinPayload.CODEC);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> split.mining().onBreak(level, player, pos, state));

		LOGGER.info("Challenges-Mod geladen{}", worldWasReset ? " (neue Welt)" : "");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
