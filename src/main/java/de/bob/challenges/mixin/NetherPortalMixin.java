package de.bob.challenges.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;

import de.bob.challenges.army.ArmyTeam;

/** Mob Army Battle: Netherportale in einer Team-Welt führen in den Team-Nether (und zurück). */
@Mixin(NetherPortalBlock.class)
public class NetherPortalMixin {
	@WrapOperation(method = "getPortalDestination",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"),
			require = 0)
	private ServerLevel challenges$teamNether(MinecraftServer server, ResourceKey<Level> key, Operation<ServerLevel> original,
			@Local(argsOnly = true) ServerLevel from) {
		ArmyTeam team = ArmyTeam.ofLevel(from.dimension());
		if (team != null) {
			ResourceKey<Level> target = from.dimension().equals(team.nether) ? team.world : team.nether;
			ServerLevel level = original.call(server, target);
			if (level != null) {
				return level;
			}
		}
		return original.call(server, key);
	}
}
