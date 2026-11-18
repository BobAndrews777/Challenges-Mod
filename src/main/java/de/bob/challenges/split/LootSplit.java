package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Split 11 „Item-Loot“: Mob-Drops und Kisten-Loot bekommen eine zufällige Farbe.
 * Nur der Spieler mit dieser Farbe kann sie einsammeln.
 */
public final class LootSplit {
	private static final String KEY_PREFIX = "challenges_loot_";

	private final SplitManager split;

	LootSplit(SplitManager split) {
		this.split = split;
	}

	/** Färbt frisch erzeugten Loot (aus LootTableEvents.MODIFY_DROPS). */
	public void tagLoot(Holder<LootTable> holder, List<ItemStack> drops) {
		if (!split.isActive(SplitType.LOOT) || holder.unwrapKey().isEmpty()) {
			return;
		}
		String path = holder.unwrapKey().get().identifier().getPath();
		if (!path.startsWith("entities/") && !path.startsWith("chests/")) {
			return;
		}
		List<SplitColor> colors = new ArrayList<>();
		for (String name : split.settings().playerColors.values()) {
			try {
				colors.add(SplitColor.valueOf(name));
			} catch (IllegalArgumentException ignored) {
			}
		}
		if (colors.isEmpty()) {
			return;
		}
		for (ItemStack stack : drops) {
			if (!stack.isEmpty()) {
				SplitColor color = colors.get(split.random().nextInt(colors.size()));
				CompoundTag tag = new CompoundTag();
				tag.putBoolean(KEY_PREFIX + color.name(), true);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
		}
	}

	public static SplitColor ownerOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		for (SplitColor color : SplitColor.values()) {
			if (tag.contains(KEY_PREFIX + color.name())) {
				return color;
			}
		}
		return null;
	}

	/** Darf dieser Spieler das Item vom Boden aufheben? (aus dem ItemEntity-Mixin) */
	public boolean canPickUp(Player player, ItemStack stack) {
		if (!split.isActive(SplitType.LOOT) || !(player instanceof ServerPlayer)) {
			return true;
		}
		SplitColor owner = ownerOf(stack);
		return owner == null || owner == split.colorOf(player);
	}

	/** Gefärbte Items auf dem Boden bekommen ein farbiges Schild. */
	public void onEntityLoad(Entity entity) {
		if (!split.isActive(SplitType.LOOT) || !(entity instanceof ItemEntity item)) {
			return;
		}
		SplitColor owner = ownerOf(item.getItem());
		if (owner != null) {
			item.setCustomName(Component.literal("■ " + owner.germanName).withStyle(owner.chat, ChatFormatting.BOLD));
			item.setCustomNameVisible(true);
		}
	}

	/**
	 * Alle 20 Ticks: Eigene Items verlieren die Farbe, fremde (z. B. aus Kisten genommene)
	 * werden wieder fallen gelassen.
	 */
	void tick(MinecraftServer server) {
		if (!split.isActive(SplitType.LOOT)) {
			return;
		}
		for (ServerPlayer player : split.coloredPlayers(server)) {
			SplitColor own = split.colorOf(player);
			Inventory inventory = player.getInventory();
			List<ItemStack> foreign = new ArrayList<>();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				SplitColor owner = ownerOf(stack);
				if (owner == null) {
					continue;
				}
				if (owner == own) {
					stack.remove(DataComponents.CUSTOM_DATA);
				} else {
					foreign.add(stack);
					inventory.setItem(i, ItemStack.EMPTY);
				}
			}
			for (ItemStack stack : foreign) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
			if (!foreign.isEmpty()) {
				player.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("Das gehört dir nicht – nur die richtige Farbe kann es einsammeln!").withStyle(ChatFormatting.RED)));
			}
		}
	}
}
