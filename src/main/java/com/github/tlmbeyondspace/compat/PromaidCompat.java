package com.github.tlmbeyondspace.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** Promaid 的可选软兼容入口；类路径中没有 Promaid 时不会加载其任何类。 */
public final class PromaidCompat {
    private static final String MOD_ID = "promaid";
    private static final String CONFIG_CLASS = "com.maidsmart.config.MaidSmartConfig";
    private static final String SCHEDULE_GUARD_CLASS = "com.maidsmart.schedule.ScheduleSwitchGuard";
    private static final String SELF_PRESERVING_TAG = "maid_smart_preserving";
    private static volatile boolean scheduleGuardResolved;
    private static volatile Method scheduleGuardMethod;

    public static void applyStartupCompatibility() {
        if (!isLoaded()) {
            return;
        }
        if (BeyondSpaceCommonConfig.PROMAID_AUTO_DISABLE_DIMENSION_FOLLOW.get()) {
            disableBooleanInMemory("MISC_DIMENSION_FOLLOW", "dimension follow");
        }
        if (BeyondSpaceCommonConfig.PROMAID_AUTO_DISABLE_OWNER_DEATH_TELEPORT.get()) {
            disableBooleanInMemory("COMBAT_MASTER_DEATH_TELEPORT", "owner-death maid teleport");
        }
    }

    private static void disableBooleanInMemory(String fieldName, String description) {
        Optional<ForgeConfigSpec.ConfigValue<Boolean>> value = booleanConfigValue(fieldName);
        if (value.isEmpty()) {
            TlmBeyondSpace.LOGGER.warn("Promaid is installed, but its {} config was not found; "
                    + "Beyond Space will continue without changing it", description);
            return;
        }
        if (Boolean.TRUE.equals(value.get().get())) {
            // 只改本次运行的内存值，不主动调用 Promaid 的 save()。
            value.get().set(false);
            TlmBeyondSpace.LOGGER.info("Disabled Promaid {} in memory for this server run", description);
        }
    }

    public static boolean shouldPrioritizeSelfPreservation(EntityMaid maid) {
        return shouldPrioritizeSelfPreservation(
                BeyondSpaceCommonConfig.PROMAID_LOW_HEALTH_PRIORITY.get(),
                isLoaded(),
                maid.getPersistentData().getBoolean(SELF_PRESERVING_TAG));
    }

    /**
     * Promaid 1.1+ may guard setTask/setSchedule while its scheduler is enabled. Run through its
     * public guard when available; older Promaid versions and absent Promaid use the action directly.
     */
    public static void runTaskScheduleChange(EntityMaid maid, ResourceLocation targetTask, Runnable action) {
        if (!isLoaded()) {
            action.run();
            return;
        }
        Method method = resolveScheduleGuard();
        if (method == null) {
            action.run();
            return;
        }
        try {
            method.invoke(null, maid.getUUID(), targetTask, action);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("Promaid guarded task change failed", cause);
        } catch (IllegalAccessException | LinkageError unavailable) {
            TlmBeyondSpace.LOGGER.warn("Promaid schedule guard could not be invoked; using normal task change",
                    unavailable);
            action.run();
        }
    }

    private static Method resolveScheduleGuard() {
        if (scheduleGuardResolved) {
            return scheduleGuardMethod;
        }
        synchronized (PromaidCompat.class) {
            if (scheduleGuardResolved) {
                return scheduleGuardMethod;
            }
            try {
                Class<?> guard = Class.forName(SCHEDULE_GUARD_CLASS, false,
                        PromaidCompat.class.getClassLoader());
                scheduleGuardMethod = guard.getMethod("runInternal", java.util.UUID.class,
                        ResourceLocation.class, Runnable.class);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                TlmBeyondSpace.LOGGER.debug(
                        "Promaid schedule guard is unavailable; using normal task changes", unavailable);
            } finally {
                scheduleGuardResolved = true;
            }
            return scheduleGuardMethod;
        }
    }

    static boolean shouldPrioritizeSelfPreservation(boolean compatibilityEnabled,
                                                     boolean promaidLoaded,
                                                     boolean selfPreserving) {
        return compatibilityEnabled && promaidLoaded && selfPreserving;
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SuppressWarnings("unchecked")
    private static Optional<ForgeConfigSpec.ConfigValue<Boolean>> booleanConfigValue(String fieldName) {
        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS, false, PromaidCompat.class.getClassLoader());
            Field field = configClass.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof ForgeConfigSpec.ConfigValue<?>) {
                return Optional.of((ForgeConfigSpec.ConfigValue<Boolean>) value);
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            TlmBeyondSpace.LOGGER.debug("Could not read optional Promaid config field {}", fieldName, error);
        }
        return Optional.empty();
    }

    private PromaidCompat() {
    }
}
