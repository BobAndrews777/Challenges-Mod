package de.bob.challenges.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.mobs.MobFlags;

/**
 * Mob-Challenge: Verwandelte Mitspieler werden nicht als Spieler gezeichnet –
 * man sieht stattdessen ihren Mob (das Kostüm kommt vom Server).
 */
@Mixin(EntityRenderer.class)
public class EntityRendererHideMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$hideMobPlayers(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Entity entity) {
		if (entity instanceof Player player && player != Minecraft.getInstance().player
				&& MobFlags.dims.containsKey(player.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
