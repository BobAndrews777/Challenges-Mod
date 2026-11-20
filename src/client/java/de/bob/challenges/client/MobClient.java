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
import java.util.UUID;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import de.bob.challenges.mobs.AbilityPayload;
import de.bob.challenges.mobs.DimsPayload;
import de.bob.challenges.mobs.MobKind;
import de.bob.challenges.mobs.MotionPayload;
import de.bob.challenges.mobs.MobFlags;
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
			tickLavaWalking(client);
		});

		// Dash / Hüpfer vom Server direkt auf den eigenen Spieler anwenden
		ClientPlayNetworking.registerGlobalReceiver(MotionPayload.TYPE, (payload, context) -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				player.setDeltaMovement(payload.x(), payload.y(), payload.z());
			}
		});

		// Hitbox-Maße der verwandelten Spieler
		ClientPlayNetworking.registerGlobalReceiver(DimsPayload.TYPE, (payload, context) -> {
			MobFlags.dims.clear();
			for (int i = 0; i < payload.players().size(); i++) {
				try {
					MobFlags.dims.put(UUID.fromString(payload.players().get(i)),
							new float[] {payload.widths().get(i), payload.heights().get(i)});
				} catch (IllegalArgumentException ignored) {
				}
			}
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null) {
				for (Player player : level.players()) {
					player.refreshDimensions();
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(MobStatePayload.TYPE, (payload, context) -> {
			state = payload;
			MobFlags.clientKind = payload.kind();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			state = new MobStatePayload("", "", 0, "", 0);
			MobFlags.clientKind = "";
			MobFlags.dims.clear();
		});
		HudElementRegistry.addLast(ChallengesMod.id("mob_state"), MobClient::extract);
	}

	/**
	 * Schreiter und Magma Cube: Lava ist wie fester Boden. Sinkt man ein, wird man nach oben gedrückt,
	 * steht man auf der Oberfläche, bleibt man darauf stehen.
	 */
	private static void tickLavaWalking(Minecraft client) {
		LocalPlayer player = client.player;
		String kind = MobFlags.clientKind;
		if (player == null || client.level == null || player.isPassenger()
				|| !(kind.equals(MobKind.STRIDER.displayName) || kind.equals(MobKind.MAGMA_CUBE.displayName))) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		if (player.isInLava()) {
			player.setDeltaMovement(motion.x, Math.max(motion.y, 0.22), motion.z);
			return;
		}
		BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
		boolean lavaBelow = BuiltInRegistries.FLUID.getKey(client.level.getFluidState(below).getType()).getPath().contains("lava");
		double top = below.getY() + 1.0;
		if (lavaBelow && motion.y <= 0 && player.getY() - top < 0.25) {
			player.setPos(player.getX(), top, player.getZ());
			player.setDeltaMovement(motion.x, 0, motion.z);
			player.setOnGround(true);
			player.fallDistance = 0;
		}
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
		// Ganz unten links, unterhalb des Chats
		int x = 4;
		int y = graphics.guiHeight() - 21;
		graphics.text(minecraft.font, current.kind(), x, y - 10, 0xFFFFAA00);
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
