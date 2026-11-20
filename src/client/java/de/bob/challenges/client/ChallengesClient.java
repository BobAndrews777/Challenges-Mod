package de.bob.challenges.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import de.bob.challenges.ChallengesMod;
import de.bob.challenges.split.MobDamagePayload;
import de.bob.challenges.split.MovementPayload;
import de.bob.challenges.split.SkinPayload;
import de.bob.challenges.split.SlotInfoPayload;
import de.bob.challenges.split.SplitStatsPayload;
import de.bob.challenges.split.SplitSyncPayload;

/** Client-Teil der Mod: empfängt den Split-Zustand und zeichnet die Übersicht oben rechts. */
public final class ChallengesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SplitSyncPayload.TYPE, (payload, context) -> ClientSplitState.update(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientSplitState.clear());
		HudElementRegistry.addLast(ChallengesMod.id("split_overview"), SplitHud::extract);

		// Etappe 2: geteilte Leisten und Mob-Damage-Anzeige
		ClientPlayNetworking.registerGlobalReceiver(SplitStatsPayload.TYPE, (payload, context) -> ClientSplitState.updateStats(payload));
		ClientPlayNetworking.registerGlobalReceiver(MobDamagePayload.TYPE, (payload, context) -> ClientSplitState.updateMobDamage(payload));
		SplitBarsHud.register();
		HudElementRegistry.addLast(ChallengesMod.id("mob_damage"), MobDamageHud::extract);

		// Etappe 3: geteilte Slots
		ClientPlayNetworking.registerGlobalReceiver(SlotInfoPayload.TYPE, (payload, context) -> ClientSplitState.updateSlotInfo(payload));
		SlotColorsScreen.register();
		HudElementRegistry.addLast(ChallengesMod.id("main_hand"), MainHandHud::extract);

		// Etappe 4: Bewegung
		ClientPlayNetworking.registerGlobalReceiver(MovementPayload.TYPE, (payload, context) -> ClientSplitState.updateMovement(payload));
		ClientPlayNetworking.registerGlobalReceiver(SkinPayload.TYPE, (payload, context) -> ClientSplitState.updateSkins(payload));

		// Mob-Challenge: Fähigkeits-Tasten und Anzeige
		MobClient.register();
	}
}
