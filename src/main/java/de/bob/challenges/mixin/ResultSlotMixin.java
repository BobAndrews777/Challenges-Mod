package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

import de.bob.challenges.split.SplitManager;

/** Split 1 „Craften“: merkt sich, wer gerade gecraftet hat. */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void challenges$onCraft(Player player, ItemStack stack, CallbackInfo ci) {
		SplitManager.get().crafting().onCraft(player);
	}
}
