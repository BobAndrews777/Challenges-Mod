package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import de.bob.challenges.mobs.MobChallenge;

/** Mob-Challenge: Geschosse fliegen durch die Mob-Kostüme hindurch (sie treffen den Spieler darin). */
@Mixin(Projectile.class)
public class ProjectileHitMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$ignoreDisguise(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (target != null && !target.level().isClientSide() && MobChallenge.get().disguises().isDisguise(target)) {
			cir.setReturnValue(false);
		}
	}
}
