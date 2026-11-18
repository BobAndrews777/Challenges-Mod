package de.bob.challenges.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Zugriff auf die Position eines Inventar-Fensters, um Slots einzufärben. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int challenges$getLeftPos();

	@Accessor("topPos")
	int challenges$getTopPos();
}
