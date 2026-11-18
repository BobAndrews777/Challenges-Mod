package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import de.bob.challenges.split.Mirrors;

/**
 * Geteilte Slots: Spiegel-Items und farbige Platzhalter kann man nicht anklicken,
 * nicht verschieben und nicht per Zahlentaste tauschen.
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
	@Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
	private void challenges$blockMirrorClicks(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		if (slotIndex >= 0 && slotIndex < self.slots.size() && Mirrors.isMarked(self.slots.get(slotIndex).getItem())) {
			ci.cancel();
			return;
		}
		if (input == ContainerInput.SWAP && (button >= 0 && button < 9 || button == 40)
				&& Mirrors.isMarked(player.getInventory().getItem(button))) {
			ci.cancel();
		}
	}
}
