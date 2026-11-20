package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

import de.bob.challenges.mobs.MobFlags;

/** Mob-Challenge: Schreiter und Magma Cube können auf Lava stehen und laufen. */
@Mixin(LivingEntity.class)
public class LivingEntityLavaMixin {
	@Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true, require = 0)
	private void challenges$walkOnLava(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && MobFlags.walksOnLava((LivingEntity) (Object) this)
				&& BuiltInRegistries.FLUID.getKey(fluid.getType()).getPath().contains("lava")) {
			cir.setReturnValue(true);
		}
	}
}
