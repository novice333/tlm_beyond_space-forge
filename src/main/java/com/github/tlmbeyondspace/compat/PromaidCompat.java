package com.github.tlmbeyondspace.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.util.Optional;

/** Promaid 的可选软兼容入口；类路径中没有 Promaid 时不会加载其任何类。 */
public final class PromaidCompat {
    private static final String MOD_ID = "promaid";
    private static final String CONFIG_CLASS = "com.maidsmart.config.MaidSmartConfig";
    private static final String SELF_PRESERVING_TAG = "maid_smart_preserving";

    public static void applyStartupCompatibility() {
        if (!BeyondSpaceCommonConfig.PROMAID_AUTO_DISABLE_DIMENSION_FOLLOW.get() || !isLoaded()) {
            return;
        }
        Optional<ForgeConfigSpec.ConfigValue<Boolean>> value = booleanConfigValue("MISC_DIMENSION_FOLLOW");
        if (value.isEmpty()) {
            TlmBeyondSpace.LOGGER.warn("Promaid is installed, but its dimension-follow config was not found; "
                    + "Beyond Space will continue without changing it");
            return;
        }
        if (Boolean.TRUE.equals(value.get().get())) {
            // 只改本次运行的内存值，不主动调用 Promaid 的 save()。
            value.get().set(false);
            TlmBeyondSpace.LOGGER.info("Disabled Promaid dimension follow in memory for this server run");
        }
    }

    public static boolean shouldPrioritizeSelfPreservation(EntityMaid maid) {
        return shouldPrioritizeSelfPreservation(
                BeyondSpaceCommonConfig.PROMAID_LOW_HEALTH_PRIORITY.get(),
                isLoaded(),
                maid.getPersistentData().getBoolean(SELF_PRESERVING_TAG));
    }

    public static boolean shouldHandoffOwnerDeath() {
        if (!BeyondSpaceCommonConfig.PROMAID_OWNER_DEATH_HANDOFF.get() || !isLoaded()) {
            return false;
        }
        return booleanConfigValue("COMBAT_MASTER_DEATH_TELEPORT")
                .map(ForgeConfigSpec.ConfigValue::get)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    static boolean shouldPrioritizeSelfPreservation(boolean compatibilityEnabled,
                                                     boolean promaidLoaded,
                                                     boolean selfPreserving) {
        return compatibilityEnabled && promaidLoaded && selfPreserving;
    }

    private static boolean isLoaded() {
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
