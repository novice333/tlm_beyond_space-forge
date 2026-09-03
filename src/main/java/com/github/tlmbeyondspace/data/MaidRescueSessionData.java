package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class MaidRescueSessionData {
    public static final ResourceLocation ID = new ResourceLocation(TlmBeyondSpace.MOD_ID, "rescue_session");
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

    public record Data(boolean active, RescueSessionKind kind, ResourceLocation sourceTask,
                       ResourceLocation combatTask, ResourceLocation originDimension, Vec3 origin,
                       boolean sourceHomeMode, MaidSchedule sourceSchedule, long startedAt,
                       int quietTicks, RescueMode triggerMode, boolean sittingCaptured,
                       boolean sourceSitting, boolean returnPending) {
        public Data(boolean active, RescueSessionKind kind, ResourceLocation sourceTask,
                    ResourceLocation combatTask, ResourceLocation originDimension, Vec3 origin,
                    boolean sourceHomeMode, MaidSchedule sourceSchedule, long startedAt,
                    int quietTicks, RescueMode triggerMode) {
            this(active, kind, sourceTask, combatTask, originDimension, origin, sourceHomeMode,
                    sourceSchedule, startedAt, quietTicks, triggerMode, false, false,
                    false);
        }

        public Data(boolean active, RescueSessionKind kind, ResourceLocation sourceTask,
                    ResourceLocation combatTask, ResourceLocation originDimension, Vec3 origin,
                    boolean sourceHomeMode, MaidSchedule sourceSchedule, long startedAt,
                    int quietTicks, RescueMode triggerMode, boolean sittingCaptured,
                    boolean sourceSitting) {
            this(active, kind, sourceTask, combatTask, originDimension, origin, sourceHomeMode,
                    sourceSchedule, startedAt, quietTicks, triggerMode, sittingCaptured,
                    sourceSitting, false);
        }

        public static Data empty() {
            return new Data(false, RescueSessionKind.REGULAR, null, null, null, Vec3.ZERO,
                    false, MaidSchedule.ALL, 0L, 0, RescueMode.FORBIDDEN, false, false,
                    false);
        }

        public Data withQuietTicks(int value) {
            return new Data(active, kind, sourceTask, combatTask, originDimension, origin,
                    sourceHomeMode, sourceSchedule, startedAt, value, triggerMode,
                    sittingCaptured, sourceSitting, returnPending);
        }

        public Data asReturnPending() {
            return new Data(false, kind, sourceTask, combatTask, originDimension, origin,
                    sourceHomeMode, sourceSchedule, startedAt, quietTicks, triggerMode,
                    sittingCaptured, sourceSitting, true);
        }

        public boolean recoveryTracked() {
            return active || returnPending;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Active", active);
            tag.putString("Kind", kind.name());
            if (sourceTask != null) tag.putString("SourceTask", sourceTask.toString());
            if (combatTask != null) tag.putString("CombatTask", combatTask.toString());
            if (originDimension != null) tag.putString("OriginDimension", originDimension.toString());
            tag.putDouble("OriginX", origin.x);
            tag.putDouble("OriginY", origin.y);
            tag.putDouble("OriginZ", origin.z);
            tag.putBoolean("SourceHomeMode", sourceHomeMode);
            tag.putString("SourceSchedule", sourceSchedule.name());
            tag.putLong("StartedAt", startedAt);
            tag.putInt("QuietTicks", quietTicks);
            tag.putString("TriggerMode", triggerMode.name());
            tag.putBoolean("SittingCaptured", sittingCaptured);
            tag.putBoolean("SourceSitting", sourceSitting);
            tag.putBoolean("ReturnPending", returnPending);
            return tag;
        }

        public static Data load(CompoundTag tag) {
            RescueSessionKind kind = parseEnum(RescueSessionKind.class, tag.getString("Kind"),
                    RescueSessionKind.REGULAR);
            MaidSchedule schedule = parseEnum(MaidSchedule.class, tag.getString("SourceSchedule"), MaidSchedule.ALL);
            return new Data(tag.getBoolean("Active"), kind,
                    ResourceLocation.tryParse(tag.getString("SourceTask")),
                    ResourceLocation.tryParse(tag.getString("CombatTask")),
                    ResourceLocation.tryParse(tag.getString("OriginDimension")),
                    new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ")),
                    tag.getBoolean("SourceHomeMode"), schedule, tag.getLong("StartedAt"),
                    Math.max(0, tag.getInt("QuietTicks")),
                    parseEnum(RescueMode.class, tag.getString("TriggerMode"), RescueMode.BOND),
                    tag.getBoolean("SittingCaptured"), tag.getBoolean("SourceSitting"),
                    tag.getBoolean("ReturnPending"));
        }

        private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private MaidRescueSessionData() {
    }
}
