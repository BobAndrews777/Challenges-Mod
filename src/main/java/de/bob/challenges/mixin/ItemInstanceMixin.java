package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemInstance;

import de.bob.challenges.split.SplitFlags;

/** Split 22 „Stacks“: Maximal 16 pro Stapel. */
@Mixin(ItemInstance.class)
public interface ItemInstanceMixin {
	@Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true, require = 0)
	private void challenges$limitStackSize(CallbackInfoReturnable<Integer> cir) {
		int limited = SplitFlags.limitStackSize(cir.getReturnValue());
		if (limited != cir.getReturnValue()) {
			cir.setReturnValue(limited);
		}
	}
}
