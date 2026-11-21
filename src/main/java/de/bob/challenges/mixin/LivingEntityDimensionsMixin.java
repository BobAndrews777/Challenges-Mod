package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.mobs.MobFlags;

/**
 * Mob-Challenge: Die Hitbox des Spielers ist exakt die des Original-Mobs (Breite × Höhe aus dem Spiel),
 * egal in welcher Haltung (stehen, schleichen, schwimmen …).
 */
@Mixin(LivingEntity.class)
public class LivingEntityDimensionsMixin {
	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true, require = 0)
	private void challenges$exactMobHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (!((Object) this instanceof Player player) || pose == Pose.SLEEPING || player.getUUID() == null) {
			return;
		}
		float[] size = MobFlags.dims.get(player.getUUID());
		if (size != null) {
			MobFlags.dimsMixinWorks = true;
			cir.setReturnValue(EntityDimensions.fixed(size[0], size[1]));
		}
	}
}
