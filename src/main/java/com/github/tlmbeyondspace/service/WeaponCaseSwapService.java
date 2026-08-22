package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.tlmbeyondspace.data.MaidWeaponSwapData;
import com.github.tlmbeyondspace.item.MaidWeaponCaseItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.UUID;

public final class WeaponCaseSwapService {
    private static final String SWAP_TOKEN_TAG = "BeyondSpaceWeaponSwapToken";

    public static boolean beginSwap(EntityMaid maid) {
        return beginSwapInternal(maid, null);
    }

    public static boolean beginSwap(EntityMaid maid, IMaidTask combatTask) {
        return RescueTaskClassifier.isCombatTask(combatTask) && beginSwapInternal(maid, combatTask);
    }

    private static boolean beginSwapInternal(EntityMaid maid, IMaidTask combatTask) {
        if (maid.level().isClientSide || MaidWeaponSwapData.get(maid).active()) {
            return false;
        }
        BaubleItemHandler baubles = maid.getMaidBauble();
        if (baubles == null) {
            return false;
        }
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            ItemStack weaponCase = baubles.getStackInSlot(slot);
            if (!(weaponCase.getItem() instanceof MaidWeaponCaseItem)) {
                continue;
            }
            ItemStack weapon = MaidWeaponCaseItem.getStoredStack(weaponCase);
            if (!MaidWeaponCaseItem.canStore(weapon)) {
                continue;
            }
            if (combatTask != null) {
                boolean compatible;
                try {
                    compatible = CombatTaskCompatibility.isWeapon(maid, combatTask, weapon);
                } catch (Exception | LinkageError ignored) {
                    compatible = false;
                }
                if (!compatible) {
                    continue;
                }
            }
            ItemStack originalMainHand = maid.getMainHandItem().copy();
            UUID token = UUID.randomUUID();
            markAsAutomaticWeapon(weapon, token);

            ItemStack changedCase = weaponCase.copy();
            MaidWeaponCaseItem.setStoredStack(changedCase, originalMainHand);
            baubles.setStackInSlot(slot, changedCase);
            maid.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            MaidWeaponSwapData.set(maid, new MaidWeaponSwapData.Data(true, slot, token, originalMainHand));
            return true;
        }
        return false;
    }

    public static boolean restoreIfActive(EntityMaid maid) {
        MaidWeaponSwapData.Data state = MaidWeaponSwapData.get(maid);
        if (!state.active()) {
            return false;
        }

        BaubleItemHandler baubles = maid.getMaidBauble();
        boolean slotValid = baubles != null && state.caseSlot() >= 0 && state.caseSlot() < baubles.getSlots();
        ItemStack weaponCase = slotValid ? baubles.getStackInSlot(state.caseSlot()) : ItemStack.EMPTY;
        boolean caseMatches = weaponCase.getItem() instanceof MaidWeaponCaseItem
                && serializedEquals(MaidWeaponCaseItem.getStoredStack(weaponCase), state.expectedCaseStack());
        ItemStack currentMainHand = maid.getMainHandItem();

        if (hasToken(currentMainHand, state.token())) {
            boolean restored = restoreFromMainHand(maid, baubles, weaponCase, state, caseMatches, currentMainHand);
            MaidWeaponSwapData.clear(maid);
            return restored;
        }

        ItemStackHandler inventory = maid.getMaidInv();
        int tokenSlot = findTokenSlot(inventory, state.token());
        if (tokenSlot >= 0) {
            ItemStack automaticWeapon = inventory.getStackInSlot(tokenSlot).copy();
            if (caseMatches) {
                removeAutomaticMarker(automaticWeapon);
                ItemStack originalMainHand = MaidWeaponCaseItem.getStoredStack(weaponCase);
                ItemStack changedCase = weaponCase.copy();
                MaidWeaponCaseItem.setStoredStack(changedCase, automaticWeapon);
                inventory.setStackInSlot(tokenSlot, currentMainHand.copy());
                baubles.setStackInSlot(state.caseSlot(), changedCase);
                maid.setItemSlot(EquipmentSlot.MAINHAND, originalMainHand);
                MaidWeaponSwapData.clear(maid);
                return true;
            }
            removeAutomaticMarker(automaticWeapon);
            inventory.setStackInSlot(tokenSlot, automaticWeapon);
        }

        MaidWeaponSwapData.clear(maid);
        return false;
    }

    private static boolean restoreFromMainHand(EntityMaid maid, BaubleItemHandler baubles,
                                               ItemStack weaponCase, MaidWeaponSwapData.Data state,
                                               boolean caseMatches, ItemStack currentMainHand) {
        ItemStack automaticWeapon = currentMainHand.copy();
        removeAutomaticMarker(automaticWeapon);
        if (!caseMatches) {
            maid.setItemSlot(EquipmentSlot.MAINHAND, automaticWeapon);
            return false;
        }
        ItemStack originalMainHand = MaidWeaponCaseItem.getStoredStack(weaponCase);
        ItemStack changedCase = weaponCase.copy();
        MaidWeaponCaseItem.setStoredStack(changedCase, automaticWeapon);
        baubles.setStackInSlot(state.caseSlot(), changedCase);
        maid.setItemSlot(EquipmentSlot.MAINHAND, originalMainHand);
        return true;
    }

    private static int findTokenSlot(ItemStackHandler inventory, UUID token) {
        if (inventory == null || token == null) {
            return -1;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (hasToken(inventory.getStackInSlot(slot), token)) {
                return slot;
            }
        }
        return -1;
    }

    static boolean serializedEquals(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.serializeNBT().equals(second.serializeNBT());
    }

    private static void markAsAutomaticWeapon(ItemStack stack, UUID token) {
        stack.getOrCreateTag().putUUID(SWAP_TOKEN_TAG, token);
    }

    private static boolean hasToken(ItemStack stack, UUID token) {
        CompoundTag tag = stack.getTag();
        return token != null && tag != null && tag.hasUUID(SWAP_TOKEN_TAG)
                && token.equals(tag.getUUID(SWAP_TOKEN_TAG));
    }

    private static void removeAutomaticMarker(ItemStack stack) {
        stack.removeTagKey(SWAP_TOKEN_TAG);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    private WeaponCaseSwapService() {
    }
}
