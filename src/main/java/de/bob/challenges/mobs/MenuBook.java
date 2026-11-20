package de.bob.challenges.mobs;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import de.bob.challenges.ChallengesMod;
import de.bob.challenges.SettingsGui;

/** Das Challenge-Menü als Buch: Rechtsklick öffnet die Einstellungen. Bekommt jeder OP beim Betreten. */
public final class MenuBook {
	private static final String MARK = "challenges_menu_book";

	private MenuBook() {
	}

	public static boolean isMenuBook(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARK);
	}

	public static ItemStack create() {
		ItemStack book = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("book")));
		book.set(DataComponents.CUSTOM_NAME, Component.literal("Challenge-Menü").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	public static boolean isOperator(ServerPlayer player) {
		return PermissionPredicates.require(ChallengesMod.id("admin"), PermissionLevel.GAMEMASTERS)
				.test(player.createCommandSourceStack());
	}

	/** Beim Betreten: OPs ohne Buch bekommen eins. */
	public static void giveIfMissing(ServerPlayer player) {
		if (!isOperator(player)) {
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (isMenuBook(player.getInventory().getItem(i))) {
				return;
			}
		}
		player.getInventory().placeItemBackInInventory(create(), Prediction.SERVER_ONLY);
	}

	/** Rechtsklick mit dem Buch öffnet das Menü. */
	public static InteractionResult onUse(Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer) || !isMenuBook(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		if (!isOperator(serverPlayer)) {
			return InteractionResult.FAIL;
		}
		SettingsGui.open(serverPlayer);
		return InteractionResult.SUCCESS;
	}
}
