package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import de.bob.challenges.split.SplitManager;

/** Split 11 „Item-Loot“: Fremd gefärbte Items können nicht aufgehoben werden. */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void challenges$checkLootOwner(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (!SplitManager.get().loot().canPickUp(player, self.getItem())) {
			ci.cancel();
		}
	}
}
