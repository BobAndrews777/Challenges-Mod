package de.bob.challenges.split;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Split 8 „Mob Damage“: Jeder muss seinen Anteil der Mob-Lebenspunkte selbst abziehen.
 * Erst wenn der Letzte seinen Anteil erfüllt hat, stirbt der Mob.
 */
public final class MobDamageSplit {
	private final SplitManager split;
	/** Mob-UUID → bereits verursachter Schaden je Farbe */
	private final Map<UUID, float[]> dealt = new HashMap<>();
	/** Treffer, die schon in ALLOW_DEATH verbucht wurden */
	private final Set<UUID> alreadyCounted = new HashSet<>();

	MobDamageSplit(SplitManager split) {
		this.split = split;
	}

	private boolean applies(LivingEntity entity) {
		return split.isActive(SplitType.MOB_DAMAGE) && !(entity instanceof Player);
	}

	private ServerPlayer attacker(DamageSource source) {
		return source.getEntity() instanceof ServerPlayer player && split.colorOf(player) != null ? player : null;
	}

	private float quota(LivingEntity entity) {
		return entity.getMaxHealth() / Math.max(1, split.playerCount());
	}

	private float[] dealtFor(LivingEntity entity) {
		return dealt.computeIfAbsent(entity.getUUID(), k -> new float[4]);
	}

	private boolean allDone(LivingEntity entity, float[] values) {
		float quota = quota(entity) - 0.01f;
		for (String colorName : split.settings().playerColors.values()) {
			try {
				int index = SplitColor.valueOf(colorName).ordinal();
				if (values[index] < quota) {
					return false;
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
		return true;
	}

	private float totalDealt(float[] values) {
		return values[0] + values[1] + values[2] + values[3];
	}

	/** Wer seinen Anteil schon erfüllt hat, macht keinen Schaden mehr. */
	public boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!applies(entity)) {
			return true;
		}
		ServerPlayer player = attacker(source);
		if (player == null) {
			return true;
		}
		int color = split.colorOf(player).ordinal();
		if (dealtFor(entity)[color] >= quota(entity) - 0.01f) {
			player.connection.send(new ClientboundSetActionBarTextPacket(
					Component.literal("Dein Anteil ist erfüllt – jetzt sind die anderen dran!").withStyle(ChatFormatting.YELLOW)));
			return false;
		}
		return true;
	}

	/** Schaden verbuchen. Zu viel Schaden wird zurückgegeben, damit die anderen ihren Anteil behalten. */
	public void afterDamage(LivingEntity entity, DamageSource source, float damageTaken) {
		if (!applies(entity) || !entity.isAlive()) {
			return;
		}
		ServerPlayer player = attacker(source);
		if (player == null) {
			return;
		}
		if (alreadyCounted.remove(entity.getUUID())) {
			return;
		}
		float[] values = dealtFor(entity);
		int color = split.colorOf(player).ordinal();
		float quota = quota(entity);
		values[color] = Math.min(quota, values[color] + damageTaken);
		if (!allDone(entity, values)) {
			float expected = Math.max(1.0f, entity.getMaxHealth() - totalDealt(values));
			if (entity.getHealth() < expected) {
				entity.setHealth(expected);
			}
		}
		send(entity, values, false);
	}

	/** Tödlicher Treffer, obwohl noch nicht alle dran waren: Mob überlebt. */
	public boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!applies(entity)) {
			return true;
		}
		ServerPlayer player = attacker(source);
		if (player == null) {
			return true;
		}
		float[] values = dealtFor(entity);
		int color = split.colorOf(player).ordinal();
		values[color] = Math.min(quota(entity), values[color] + Math.max(0, amount));
		alreadyCounted.add(entity.getUUID());
		if (allDone(entity, values)) {
			return true;
		}
		entity.setHealth(Math.max(1.0f, entity.getMaxHealth() - totalDealt(values)));
		send(entity, values, false);
		return false;
	}

	public void onRemoved(LivingEntity entity) {
		if (dealt.remove(entity.getUUID()) != null) {
			send(entity, new float[4], true);
		}
		alreadyCounted.remove(entity.getUUID());
	}

	private void send(LivingEntity entity, float[] values, boolean remove) {
		MinecraftServer server = entity.level().getServer();
		if (server == null) {
			return;
		}
		float quota = Math.max(0.01f, quota(entity));
		float[] progress = new float[4];
		for (int i = 0; i < 4; i++) {
			progress[i] = -1;
		}
		for (String colorName : split.settings().playerColors.values()) {
			try {
				int index = SplitColor.valueOf(colorName).ordinal();
				progress[index] = Math.min(1.0f, values[index] / quota);
			} catch (IllegalArgumentException ignored) {
			}
		}
		MobDamagePayload payload = new MobDamagePayload(entity.getId(), progress[0], progress[1], progress[2], progress[3], remove);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, MobDamagePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	void reset() {
		dealt.clear();
		alreadyCounted.clear();
	}
}
