package de.bob.challenges.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;

import de.bob.challenges.mobs.MobFlags;

/** Mob-Challenge: Das eigene Kostüm kann man nicht anklicken (sonst wäre es immer im Fadenkreuz). */
@Mixin(LivingEntity.class)
public class LivingEntityPickMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$notOwnDisguise(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (MobFlags.ownDisguiseId >= 0 && self.getId() == MobFlags.ownDisguiseId && self.level().isClientSide()) {
			cir.setReturnValue(false);
		}
	}
}
