package de.bob.challenges.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import de.bob.challenges.ChallengesMod;
import de.bob.challenges.mobs.AbilityPayload;
import de.bob.challenges.mobs.MobStatePayload;

/**
 * Client-Teil der Mob-Challenge: zwei Fähigkeits-Tasten (Standard R und G, in den
 * Steuerungs-Einstellungen änderbar) und die Anzeige „Du bist: …“ mit Abklingzeiten.
 */
public final class MobClient {
	private static KeyMapping ability1;
	private static KeyMapping ability2;
	private static volatile MobStatePayload state = new MobStatePayload("", "", 0, "", 0);

	private MobClient() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(ChallengesMod.id("challenges"));
		ability1 = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.challenges.ability1",
				InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_R, category));
		ability2 = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.challenges.ability2",
				InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ability1.consumeClick()) {
				send(1);
			}
			while (ability2.consumeClick()) {
				send(2);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(MobStatePayload.TYPE, (payload, context) -> state = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> state = new MobStatePayload("", "", 0, "", 0));
		HudElementRegistry.addLast(ChallengesMod.id("mob_state"), MobClient::extract);
	}

	private static void send(int ability) {
		if (!state.kind().isEmpty() && ClientPlayNetworking.canSend(AbilityPayload.TYPE)) {
			ClientPlayNetworking.send(new AbilityPayload(ability));
		}
	}

	/** Unten links: Mob-Name und Fähigkeiten mit Abklingzeit. */
	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		MobStatePayload current = state;
		if (current.kind().isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int x = 4;
		int y = graphics.guiHeight() - 36;
		graphics.text(minecraft.font, current.kind(), x, y - 12, 0xFFFFAA00);
		if (!current.ability1().isEmpty()) {
			graphics.text(minecraft.font, line(ability1, current.ability1(), current.cooldown1()), x, y,
					current.cooldown1() > 0 ? 0xFFAAAAAA : 0xFF55FF55);
		}
		if (!current.ability2().isEmpty()) {
			graphics.text(minecraft.font, line(ability2, current.ability2(), current.cooldown2()), x, y + 10,
					current.cooldown2() > 0 ? 0xFFAAAAAA : 0xFF55FF55);
		}
	}

	private static String line(KeyMapping key, String name, int cooldown) {
		String keyName = key.getTranslatedKeyMessage().getString();
		return "[" + keyName + "] " + name + (cooldown > 0 ? " (" + ((cooldown + 19) / 20) + " s)" : "");
	}
}
