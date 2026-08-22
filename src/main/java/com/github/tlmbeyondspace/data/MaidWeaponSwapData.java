package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class MaidWeaponSwapData {
    public static final ResourceLocation ID = new ResourceLocation(TlmBeyondSpace.MOD_ID, "weapon_case_swap");
    public static final TaskDataKey<Data> KEY = new TaskDataKey<>() {
        @Override
        public ResourceLocation getKey() {
            return ID;
        }

        @Override
        public CompoundTag writeSaveData(Data value) {
            return value.save();
        }

        @Override
        public Data readSaveData(CompoundTag tag) {
            return Data.load(tag);
        }
    };

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, Data.empty());
    }

    public static void set(EntityMaid maid, Data data) {
        maid.setData(KEY, data);
    }

    public static void clear(EntityMaid maid) {
        maid.setData(KEY, Data.empty());
    }

    public record Data(boolean active, int caseSlot, UUID token, ItemStack expectedCaseStack) {
        public Data {
            expectedCaseStack = expectedCaseStack == null ? ItemStack.EMPTY : expectedCaseStack.copy();
        }

        @Override
        public ItemStack expectedCaseStack() {
            return expectedCaseStack.copy();
        }

        public static Data empty() {
            return new Data(false, -1, null, ItemStack.EMPTY);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Active", active);
            if (!active || token == null || caseSlot < 0) {
                return tag;
            }
            tag.putInt("CaseSlot", caseSlot);
            tag.putUUID("Token", token);
            if (!expectedCaseStack.isEmpty()) {
                tag.put("ExpectedCaseStack", expectedCaseStack.serializeNBT());
            }
            return tag;
        }

        public static Data load(CompoundTag tag) {
            if (!tag.getBoolean("Active") || !tag.hasUUID("Token") || tag.getInt("CaseSlot") < 0) {
                return empty();
            }
            ItemStack expected = tag.contains("ExpectedCaseStack", Tag.TAG_COMPOUND)
                    ? ItemStack.of(tag.getCompound("ExpectedCaseStack")) : ItemStack.EMPTY;
            return new Data(true, tag.getInt("CaseSlot"), tag.getUUID("Token"), expected);
        }
    }

    private MaidWeaponSwapData() {
    }
}
