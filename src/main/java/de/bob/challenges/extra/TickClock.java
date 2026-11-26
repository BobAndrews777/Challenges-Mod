package de.bob.challenges.extra;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Die Uhr, mit der man das Spieltempo umstellt (Normal → Ganz schnell → Ganz langsam). */
final class TickClock {
	private static final String MARK = "challenges_tick_clock";

	private TickClock() {
	}

	static boolean isClock(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARK);
	}

	static ItemStack create() {
		ItemStack clock = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("clock")));
		clock.set(DataComponents.CUSTOM_NAME, Component.literal("Zeit-Uhr (Rechtsklick)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		clock.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		clock.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return clock;
	}

	static void giveIfMissing(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (isClock(player.getInventory().getItem(i))) {
				return;
			}
		}
		if (isClock(player.containerMenu.getCarried())) {
			return;
		}
		player.getInventory().add(create());
	}
}
