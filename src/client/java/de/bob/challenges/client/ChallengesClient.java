package de.bob.challenges.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import de.bob.challenges.ChallengesMod;
import de.bob.challenges.split.SplitSyncPayload;

/** Client-Teil der Mod: empfängt den Split-Zustand und zeichnet die Übersicht oben rechts. */
public final class ChallengesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SplitSyncPayload.TYPE, (payload, context) -> ClientSplitState.update(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientSplitState.clear());
		HudElementRegistry.addLast(ChallengesMod.id("split_overview"), SplitHud::extract);
	}
}
