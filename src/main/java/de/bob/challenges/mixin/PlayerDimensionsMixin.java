package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.mobs.MobFlags;

/** Mob-Challenge: Die Hitbox des Spielers bekommt genau die Maße des Mobs. */
@Mixin(Player.class)
public class PlayerDimensionsMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$mobDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		MobFlags.dimsMixinWorks = true;
		Player self = (Player) (Object) this;
		if (pose == Pose.SLEEPING || self.getUUID() == null) {
			return;
		}
		float[] size = MobFlags.dims.get(self.getUUID());
		if (size != null) {
			// Die Größe (Scale) wird danach noch draufgerechnet – darum hier herausrechnen
			float scale = Math.max(0.01f, self.getScale());
			cir.setReturnValue(EntityDimensions.scalable(size[0] / scale, size[1] / scale));
		}
	}
}
