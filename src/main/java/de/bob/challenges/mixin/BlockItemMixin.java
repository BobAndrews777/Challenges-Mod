package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import de.bob.challenges.split.SplitFlags;
import de.bob.challenges.split.SplitManager;

/** Split 14 „Platzieren“: Nur wer an der Reihe ist, darf einen Block setzen. */
@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD"), cancellable = true)
	private void challenges$checkPlaceTurn(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		Player player = context.getPlayer();
		if (player != null && !SplitManager.get().canPlaceWith(player, context.getItemInHand())) {
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}
		if (player != null && !SplitFlags.canPlace(player.getName().getString())) {
			if (player instanceof ServerPlayer serverPlayer) {
				SplitManager.get().notifyNotYourTurn(serverPlayer);
			}
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"))
	private void challenges$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() instanceof ServerPlayer serverPlayer && cir.getReturnValue().consumesAction()) {
			SplitManager.get().onBlockPlaced(serverPlayer);
		}
	}
}
