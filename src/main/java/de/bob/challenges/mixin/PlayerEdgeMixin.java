package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.split.SplitFlags;

/**
 * Split 9 „Sneaken“: Der Schutz vor dem Herunterfallen an Blockkanten wirkt nur,
 * wenn alle gleichzeitig schleichen. Die Bewegung rechnet der Client, daher greift das beim eigenen Spieler.
 */
@Mixin(Player.class)
public class PlayerEdgeMixin {
	@Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true, require = 0)
	private void challenges$sharedSneak(CallbackInfoReturnable<Boolean> cir) {
		if (SplitFlags.edgeProtectionDisabled && !((Object) this instanceof ServerPlayer) && cir.getReturnValueZ()) {
			cir.setReturnValue(false);
		}
	}
}
