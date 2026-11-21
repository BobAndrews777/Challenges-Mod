package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import de.bob.challenges.army.ArmyBattle;

/** Mob Army Battle: Im Kampf zerstören Explosionen (TNT, Creeper, Wither …) in den Team-Welten keine Blöcke. */
@Mixin(ServerExplosion.class)
public class ExplosionMixin {
	@Shadow @Final private ServerLevel level;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$protectArena(CallbackInfo ci) {
		if (ArmyBattle.get().protectsBlocks(this.level)) {
			ci.cancel();
		}
	}
}
