package com.github.tlmbeyondspace.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class MaidUsefulTaskCompat {
    private static boolean initialized;
    private static boolean available;
    private static Method setCurrent;
    private static Method clearTarget;
    private static Object idle;

    public static void resetTransientWork(EntityMaid maid) {
        initialize();
        if (!available) return;
        try {
            clearTarget.invoke(null, maid);
            setCurrent.invoke(null, maid, idle);
        } catch (ReflectiveOperationException exception) {
            available = false;
            TlmBeyondSpace.LOGGER.warn("Disabling maid_useful_task compatibility after invocation failure", exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("maid_useful_task")) return;
        try {
            Class<?> memoryUtil = Class.forName("studio.fantasyit.maid_useful_task.util.MemoryUtil");
            Class<? extends Enum> currentWork = (Class<? extends Enum>) Class.forName(
                    "studio.fantasyit.maid_useful_task.memory.CurrentWork").asSubclass(Enum.class);
            setCurrent = memoryUtil.getMethod("setCurrent", EntityMaid.class, currentWork);
            clearTarget = memoryUtil.getMethod("clearTarget", EntityMaid.class);
            idle = Enum.valueOf(currentWork, "IDLE");
            available = true;
        } catch (ReflectiveOperationException exception) {
            TlmBeyondSpace.LOGGER.warn("maid_useful_task is loaded but its compatibility API was not recognized", exception);
        }
    }

    private MaidUsefulTaskCompat() {
    }
}
