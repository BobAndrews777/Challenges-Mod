package de.bob.challenges.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import de.bob.challenges.mobs.MobFlags;

/**
 * Mob-Challenge: Für Schreiter und Magma Cube ist die Lava-Oberfläche fester Boden
 * (gilt auf Server und Client, deshalb ruckelt nichts).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LavaCollisionMixin {
	@Unique
	private static final double CHALLENGES$TOP = 0.9;
	@Unique
	private static final VoxelShape CHALLENGES$SHAPE = Shapes.box(0, 0, 0, 1, CHALLENGES$TOP, 1);

	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void challenges$lavaFloor(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		FluidState fluid = ((BlockState) (Object) this).getFluidState();
		if (fluid.isEmpty() || !fluid.is(FluidTags.LAVA) || !(context instanceof EntityCollisionContext entityContext)) {
			return;
		}
		Entity entity = entityContext.getEntity();
		if (entity == null || !MobFlags.walksOnLava(entity)) {
			return;
		}
		// Nur die oberste Lava-Schicht, und nur wenn man nicht schon drinsteckt
		if (level.getFluidState(pos.above()).is(FluidTags.LAVA) || entity.getY() < pos.getY() + CHALLENGES$TOP - 0.05) {
			return;
		}
		cir.setReturnValue(CHALLENGES$SHAPE);
	}
}
