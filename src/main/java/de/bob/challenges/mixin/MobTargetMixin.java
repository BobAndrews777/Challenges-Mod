package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import de.bob.challenges.mobs.MobChallenge;

/** Mob-Challenge: Mobs der eigenen Art (und eigene Helfer) nehmen den Spieler gar nicht erst ins Visier. */
@Mixin(Mob.class)
public class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$sameKindPeace(LivingEntity target, CallbackInfo ci) {
		if (target != null && !MobChallenge.get().mayTarget((Mob) (Object) this, target)) {
			ci.cancel();
		}
	}

	/** Mob-Kostüme werden nie entfernt – auch nicht auf „Friedlich“, wo sonst alle Monster sofort verschwinden. */
	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$keepDisguise(CallbackInfo ci) {
		if (MobChallenge.get().disguises().isDisguise((Mob) (Object) this) || de.bob.challenges.army.ArmyBattle.get().isArmyMob((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
