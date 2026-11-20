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
import de.bob.challenges.mobs.ReelPayload;
import de.bob.challenges.mobs.DisguiseMapPayload;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Client-Teil der Mob-Challenge: zwei Fähigkeits-Tasten (Standard R und G, in den
 * Steuerungs-Einstellungen änderbar) und die Anzeige „Du bist: …“ mit Abklingzeiten.
 */
public final class MobClient {
	private static KeyMapping ability1;
	private static KeyMapping ability2;
	private static volatile MobStatePayload state = new MobStatePayload("", "", 0, "", 0);
	private static long clientTicks;
	private static long dashReadyAt;
	private static boolean wasJump;
	private static volatile Reel reel;
	private static volatile Map<Integer, UUID> disguises = Map.of();
	private static int lastReelIndex = -1;

	/** Glücksrad-Walze: Namen laufen von oben nach unten durch und bleiben auf dem ausgelosten Mob stehen. */
	private record Reel(String player, String kind, long start, long spinMs, long totalMs, List<String> strip, int target) {
	}

	private MobClient() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(ChallengesMod.id("challenges"));
		ability1 = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.challenges.ability1",
				InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_R, category));
		ability2 = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.challenges.ability2",
				InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTicks++;
			while (ability1.consumeClick()) {
				if (!localDash(client, false)) {
					send(1);
				}
			}
			while (ability2.consumeClick()) {
				send(2);
			}
			// Kamel: Leertaste = Dash
			boolean jump = client.player != null && client.options.keyJump.isDown();
			if (jump && !wasJump) {
				localDash(client, true);
			}
			wasJump = jump;
			tickLavaWalking(client);
			snapDisguises(client);
		});

		ClientPlayNetworking.registerGlobalReceiver(ReelPayload.TYPE, (payload, context) -> startReel(payload));
		ClientPlayNetworking.registerGlobalReceiver(DisguiseMapPayload.TYPE, (payload, context) -> {
			Map<Integer, UUID> map = new HashMap<>();
			int own = -1;
			LocalPlayer self = Minecraft.getInstance().player;
			for (int i = 0; i < payload.ids().size(); i++) {
				try {
					UUID owner = UUID.fromString(payload.owners().get(i));
					map.put(payload.ids().get(i), owner);
					if (self != null && owner.equals(self.getUUID())) {
						own = payload.ids().get(i);
					}
				} catch (IllegalArgumentException ignored) {
				}
			}
			disguises = map;
			MobFlags.ownDisguiseId = own;
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
			if (payload.kind().isEmpty()) {
				MobFlags.ownDisguiseId = -1;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			state = new MobStatePayload("", "", 0, "", 0);
			MobFlags.clientKind = "";
			MobFlags.dims.clear();
			MobFlags.ownDisguiseId = -1;
			disguises = Map.of();
		});
		HudElementRegistry.addLast(ChallengesMod.id("mob_state"), MobClient::extract);
		HudElementRegistry.addLast(ChallengesMod.id("mob_reel"), MobClient::extractReel);
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
		// Steckt man drin, wird man nach oben gedrückt – auf der Oberfläche trägt einen dann die Lava selbst
		if (player.isInLava()) {
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, Math.max(motion.y, 0.25), motion.z);
		}
	}

	/**
	 * Die Mob-Modelle kommen vom Server und würden dort hinterherhängen. Darum setzt jeder Client sie
	 * jeden Tick selbst genau auf ihren Spieler – samt Drehung und der Position vom letzten Tick,
	 * damit sie genauso flüssig gezeichnet werden wie der Spieler.
	 */
	private static void snapDisguises(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || disguises.isEmpty()) {
			return;
		}
		for (Map.Entry<Integer, UUID> entry : disguises.entrySet()) {
			Entity mob = level.getEntity(entry.getKey());
			Player owner = level.getPlayerByUUID(entry.getValue());
			if (mob == null || owner == null) {
				continue;
			}
			mob.setPos(owner.getX(), owner.getY(), owner.getZ());
			mob.xo = owner.xo;
			mob.yo = owner.yo;
			mob.zo = owner.zo;
			mob.setYRot(owner.getYRot());
			mob.yRotO = owner.yRotO;
			mob.setXRot(owner.getXRot());
			mob.xRotO = owner.xRotO;
			mob.setDeltaMovement(owner.getDeltaMovement());
			if (mob instanceof LivingEntity living) {
				living.yBodyRot = owner.yBodyRot;
				living.yBodyRotO = owner.yBodyRotO;
				living.yHeadRot = owner.yHeadRot;
				living.yHeadRotO = owner.yHeadRotO;
			}
			for (Entity passenger : mob.getPassengers()) {
				mob.positionRider(passenger);
			}
		}
	}

	/**
	 * Ziege (R) und Kamel (R oder Leertaste): Der Schwung wird direkt hier auf dem Client gegeben –
	 * sofort und ohne Umweg über den Server. Der Server kümmert sich um Treffer, Ton und Abklingzeit.
	 * @return true, wenn die Taste damit erledigt ist
	 */
	private static boolean localDash(Minecraft client, boolean jumpKey) {
		LocalPlayer player = client.player;
		String kind = MobFlags.clientKind;
		boolean goat = kind.equals(MobKind.GOAT.displayName);
		boolean camel = kind.equals(MobKind.CAMEL.displayName);
		if (player == null || player.isPassenger() || !(goat || camel) || (jumpKey && !camel)) {
			return false;
		}
		if (state.cooldown1() > 0 || clientTicks < dashReadyAt || (camel && !player.onGround())) {
			// Ziege: an den Server weiterreichen (zeigt die Abklingzeit an); Kamel: Taste einfach ignorieren
			return camel;
		}
		Vec3 look = player.getLookAngle();
		double length = Math.max(0.01, Math.sqrt(look.x * look.x + look.z * look.z));
		double speed = goat ? 1.6 : 1.5;
		double up = goat ? 0.3 : 0.42;
		player.setDeltaMovement(look.x / length * speed, up, look.z / length * speed);
		dashReadyAt = clientTicks + (goat ? 100 : 55);
		send(1);
		return true;
	}

	private static void startReel(ReelPayload payload) {
		List<String> names = new ArrayList<>();
		for (MobKind kind : MobKind.values()) {
			if (kind.ready) {
				names.add(kind.displayName);
			}
		}
		Random random = new Random();
		int target = 28;
		List<String> strip = new ArrayList<>();
		String previous = "";
		for (int i = 0; i < target + 3; i++) {
			String name;
			if (i == target) {
				name = payload.kind();
			} else {
				do {
					name = names.get(random.nextInt(names.size()));
				} while (names.size() > 2 && (name.equals(previous) || (i == target - 1 || i == target + 1) && name.equals(payload.kind())));
			}
			strip.add(name);
			previous = name;
		}
		long total = Math.max(1500, payload.ticks() * 50L);
		reel = new Reel(payload.player(), payload.kind(), System.currentTimeMillis(), total - 1000, total, strip, target);
		lastReelIndex = -1;
	}

	private static void extractReel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Reel current = reel;
		if (current == null) {
			return;
		}
		long elapsed = System.currentTimeMillis() - current.start();
		if (elapsed > current.totalMs()) {
			reel = null;
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		double t = Math.min(1.0, elapsed / (double) current.spinMs());
		double eased = 1.0 - Math.pow(1.0 - t, 4);
		double position = eased * current.target();
		boolean done = t >= 1.0;

		int index = (int) Math.round(position);
		if (index != lastReelIndex) {
			lastReelIndex = index;
			playLocal(minecraft, done ? "entity.player.levelup" : "block.note_block.hat", done ? 1.2f : 1.6f);
		}

		int row = 14;
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2 - 62;
		int half = 75;
		// Rahmen und Hintergrund
		graphics.fill(cx - half - 2, cy - 23, cx + half + 2, cy + 23, 0xFF5A3A12);
		graphics.fill(cx - half, cy - 21, cx + half, cy + 21, 0xE0101010);
		graphics.fill(cx - half, cy - 8, cx + half, cy + 8, done ? 0x60FFAA00 : 0x30FFFFFF);
		graphics.fill(cx - half, cy - 8, cx + half, cy - 7, 0xFFFFAA00);
		graphics.fill(cx - half, cy + 7, cx + half, cy + 8, 0xFFFFAA00);

		String header = current.player() + " wird zu …";
		graphics.text(minecraft.font, header, cx - minecraft.font.width(header) / 2, cy - 35, 0xFFFFFFFF);

		int first = (int) Math.floor(position) - 2;
		for (int i = Math.max(0, first); i <= first + 4 && i < current.strip().size(); i++) {
			double offset = position - i;   // > 0: unterhalb der Mitte, < 0: oberhalb – wandert nach unten
			if (Math.abs(offset) > 1.3) {
				continue;
			}
			String name = current.strip().get(i);
			int y = (int) Math.round(cy + offset * row) - 4;
			int alpha = (int) Math.round(255 * Math.max(0.25, 1.0 - Math.abs(offset) / 1.6));
			int rgb = done && i == current.target() ? 0xFFAA00 : 0xFFFFFF;
			graphics.text(minecraft.font, name, cx - minecraft.font.width(name) / 2, y, (alpha << 24) | rgb);
		}
	}

	private static void playLocal(Minecraft minecraft, String sound, float pitch) {
		LocalPlayer player = minecraft.player;
		Identifier id = Identifier.withDefaultNamespace(sound);
		if (player == null || minecraft.level == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
			return;
		}
		minecraft.level.playLocalSound(player.getX(), player.getY(), player.getZ(), BuiltInRegistries.SOUND_EVENT.getValue(id),
				SoundSource.MASTER, 0.6f, pitch, false);
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
