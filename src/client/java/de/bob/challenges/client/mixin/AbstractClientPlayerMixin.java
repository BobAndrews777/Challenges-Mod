package de.bob.challenges.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import de.bob.challenges.client.ClientSplitState;

/** Split 19 „Skins“: Spieler werden mit dem Skin eines anderen Spielers angezeigt. */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void challenges$swapSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (ClientSplitState.skinSwap.isEmpty()) {
			return;
		}
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		String other = ClientSplitState.skinSwap.get(self.getName().getString());
		if (other == null || Minecraft.getInstance().getConnection() == null) {
			return;
		}
		PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(other);
		if (info != null) {
			cir.setReturnValue(info.getSkin());
		}
	}
}
