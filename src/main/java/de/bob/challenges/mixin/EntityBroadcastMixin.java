package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import de.bob.challenges.mobs.MobChallenge;

/** Mob-Challenge: Das eigene Mob-Kostüm wird dem Spieler selbst gar nicht erst geschickt (würde die Sicht versperren). */
@Mixin(Entity.class)
public class EntityBroadcastMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$hideOwnDisguise(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (MobChallenge.get().disguises().isOwnDisguise(self, player)) {
			cir.setReturnValue(false);
		}
	}
}
