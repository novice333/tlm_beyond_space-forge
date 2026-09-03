package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DistressSignalData {
    public static final String ITEM_TAG = "BeyondSpaceDistressSignal";
    public static final int MAX_SELECTIONS = 512;
    private final UUID ownerId;
    private final Map<UUID, Selection> selections;
    private final boolean recallMode;
    private final boolean knockdownRescue;

    public DistressSignalData(UUID ownerId, Map<UUID, Selection> selections) {
        this(ownerId, selections, false, false);
    }

    public DistressSignalData(UUID ownerId, Map<UUID, Selection> selections, boolean recallMode) {
        this(ownerId, selections, recallMode, false);
    }

    public DistressSignalData(UUID ownerId, Map<UUID, Selection> selections, boolean recallMode,
                              boolean knockdownRescue) {
        this.ownerId = ownerId;
        this.selections = new LinkedHashMap<>(selections);
        this.recallMode = recallMode;
        this.knockdownRescue = knockdownRescue;
    }

    public static DistressSignalData empty() {
        return new DistressSignalData(null, Map.of());
    }

    public Optional<UUID> ownerId() {
        return Optional.ofNullable(ownerId);
    }

    public boolean canUse(UUID playerId) {
        return ownerId == null || ownerId.equals(playerId);
    }

    public Selection get(UUID maidId) {
        return selections.getOrDefault(maidId, new Selection(false, TaskAttack.UID));
    }

    public Map<UUID, Selection> selections() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(selections));
    }

    public boolean recallMode() {
        return recallMode;
    }

    public boolean knockdownRescue() {
        return knockdownRescue;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        if (ownerId != null) root.putUUID("Owner", ownerId);
        ListTag list = new ListTag();
        selections.forEach((maidId, selection) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Maid", maidId);
            entry.putBoolean("Enabled", selection.enabled());
            entry.putBoolean("LoadUnloaded", selection.loadUnloaded());
            entry.putString("CombatTask", selection.combatTask().toString());
            list.add(entry);
        });
        root.put("Selections", list);
        root.putBoolean("RecallMode", recallMode);
        root.putBoolean("KnockdownRescue", knockdownRescue);
        return root;
    }

    public static DistressSignalData load(CompoundTag root) {
        UUID owner = root.hasUUID("Owner") ? root.getUUID("Owner") : null;
        Map<UUID, Selection> selections = new LinkedHashMap<>();
        ListTag list = root.getList("Selections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && selections.size() < MAX_SELECTIONS; i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Maid")) continue;
            ResourceLocation task = ResourceLocation.tryParse(entry.getString("CombatTask"));
            if (task == null) task = TaskAttack.UID;
            boolean enabled = entry.getBoolean("Enabled");
            // Signals saved before this option existed always attempted a bounded chunk load.
            boolean loadUnloaded = !entry.contains("LoadUnloaded", Tag.TAG_BYTE)
                    || entry.getBoolean("LoadUnloaded");
            selections.put(entry.getUUID("Maid"), new Selection(enabled, loadUnloaded, task));
        }
        return new DistressSignalData(owner, selections, root.getBoolean("RecallMode"),
                root.getBoolean("KnockdownRescue"));
    }

    public static DistressSignalData fromItem(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ITEM_TAG, Tag.TAG_COMPOUND)
                ? load(tag.getCompound(ITEM_TAG)) : empty();
    }

    public static void writeItem(ItemStack stack, DistressSignalData data) {
        stack.getOrCreateTag().put(ITEM_TAG, data.save());
    }

    public record Selection(boolean enabled, boolean loadUnloaded, ResourceLocation combatTask) {
        public Selection(boolean enabled, ResourceLocation combatTask) {
            this(enabled, true, combatTask);
        }
    }
}
