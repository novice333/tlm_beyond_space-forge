package com.github.tlmbeyondspace.item;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.Stream;

public final class MaidWeaponCaseItem extends Item implements IMaidBauble {
    private static final String STORED_ITEM_TAG = "BeyondSpaceStoredItem";

    public MaidWeaponCaseItem(Properties properties) {
        super(properties);
    }

    public static ItemStack getStoredStack(ItemStack weaponCase) {
        CompoundTag root = weaponCase.getTag();
        if (root == null || !root.contains(STORED_ITEM_TAG, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(root.getCompound(STORED_ITEM_TAG)).copy();
    }

    public static void setStoredStack(ItemStack weaponCase, ItemStack stored) {
        if (stored.isEmpty()) {
            weaponCase.removeTagKey(STORED_ITEM_TAG);
            clearEmptyRootTag(weaponCase);
            return;
        }
        weaponCase.getOrCreateTag().put(STORED_ITEM_TAG, stored.copy().serializeNBT());
    }

    public static boolean canStore(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof MaidWeaponCaseItem);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack weaponCase, Slot slot, ClickAction action, Player player) {
        if (weaponCase.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack target = slot.getItem();
        ItemStack stored = getStoredStack(weaponCase);
        if (target.isEmpty()) {
            if (!stored.isEmpty() && slot.mayPlace(stored) && stored.getCount() <= slot.getMaxStackSize(stored)) {
                ItemStack remainder = slot.safeInsert(stored);
                setStoredStack(weaponCase, remainder);
            }
            return true;
        }
        if (stored.isEmpty() && canStore(target)) {
            ItemStack taken = slot.safeTake(target.getCount(), target.getCount(), player);
            if (!taken.isEmpty()) {
                setStoredStack(weaponCase, taken);
            }
        }
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack weaponCase, ItemStack carried, Slot slot,
                                            ClickAction action, Player player, SlotAccess carriedAccess) {
        if (weaponCase.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack stored = getStoredStack(weaponCase);
        if (carried.isEmpty()) {
            if (!stored.isEmpty() && carriedAccess.set(stored)) {
                setStoredStack(weaponCase, ItemStack.EMPTY);
                slot.setChanged();
            }
            return true;
        }
        if (stored.isEmpty() && canStore(carried)) {
            setStoredStack(weaponCase, carried.copy());
            carried.setCount(0);
            slot.setChanged();
        }
        return true;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !getStoredStack(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return isBarVisible(stack) ? 13 : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xD7A84A;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.maid_weapon_case.flavor.1")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.maid_weapon_case.flavor.2")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.maid_weapon_case.flavor.3")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());
        ItemStack stored = getStoredStack(stack);
        tooltip.add(stored.isEmpty()
                ? Component.translatable("tooltip.tlm_beyond_space.maid_weapon_case.empty")
                .withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.tlm_beyond_space.maid_weapon_case.stored", stored.getHoverName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.maid_weapon_case.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDestroyed(ItemEntity itemEntity) {
        ItemStack stored = getStoredStack(itemEntity.getItem());
        ItemUtils.onContainerDestroyed(itemEntity, stored.isEmpty() ? Stream.empty() : Stream.of(stored));
    }

    @Override
    public boolean syncClient(EntityMaid maid, ItemStack stack) {
        return true;
    }

    private static void clearEmptyRootTag(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root != null && root.isEmpty()) {
            stack.setTag(null);
        }
    }
}
