package de.bob.challenges.split;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * „Spiegel-Items“ für die geteilten Slots: Jeder sieht die Items der anderen,
 * aber nur als harmlose Kopie, die man weder nehmen noch benutzen kann.
 */
public final class Mirrors {
	private static final String MARK = "challenges_mirror";

	private Mirrors() {
	}

	public static boolean isMarked(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARK);
	}

	private static void mark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
		tag.putBoolean(MARK, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** Kopie eines fremden Items: sieht gleich aus, hat aber keine Wirkung. */
	public static ItemStack mirror(ItemStack original, SplitColor owner) {
		if (original.isEmpty()) {
			return placeholder(owner);
		}
		ItemStack copy = original.copy();
		boolean enchanted = copy.isEnchanted();
		copy.remove(DataComponents.EQUIPPABLE);
		copy.remove(DataComponents.GLIDER);
		copy.remove(DataComponents.ENCHANTMENTS);
		copy.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		if (enchanted) {
			copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		copy.set(DataComponents.CUSTOM_NAME, original.getHoverName().copy().withStyle(ChatFormatting.WHITE)
				.append(Component.literal(" · " + (owner != null ? owner.germanName : "")).withStyle(owner != null ? owner.chat : ChatFormatting.GRAY)));
		mark(copy);
		return copy;
	}

	/** Leerer fremder Slot: Glasscheibe in der Farbe des Besitzers (grau = gehört niemandem). */
	public static ItemStack placeholder(SplitColor owner) {
		String id = owner == null ? "gray_stained_glass_pane" : switch (owner) {
			case RED -> "red_stained_glass_pane";
			case YELLOW -> "yellow_stained_glass_pane";
			case BLUE -> "blue_stained_glass_pane";
			case GREEN -> "lime_stained_glass_pane";
		};
		Identifier key = Identifier.withDefaultNamespace(id);
		ItemStack stack = BuiltInRegistries.ITEM.containsKey(key)
				? new ItemStack(BuiltInRegistries.ITEM.getValue(key))
				: new ItemStack(net.minecraft.world.item.Items.BARRIER);
		stack.set(DataComponents.CUSTOM_NAME, owner == null
				? Component.literal("Gesperrt").withStyle(ChatFormatting.DARK_GRAY)
				: Component.literal("Bereich von " + owner.germanName).withStyle(owner.chat));
		mark(stack);
		return stack;
	}

	/** Ein echtes Item ohne Spiegel-Markierung (für den gemeinsamen Speicher). */
	public static ItemStack unmirror(ItemStack stack) {
		return isMarked(stack) ? ItemStack.EMPTY : stack.copy();
	}
}
