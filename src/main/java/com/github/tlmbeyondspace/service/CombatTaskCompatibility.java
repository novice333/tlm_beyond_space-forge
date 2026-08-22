package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.TaskEquipUtil;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges combat tasks that do not implement Touhou Little Maid's weapon contract completely.
 * Optional-mod classes are deliberately accessed through reflection so this mod remains loadable
 * when those mods are absent.
 */
public final class CombatTaskCompatibility {
    public static final ResourceLocation EPIC_FIGHT_TASK =
            new ResourceLocation("ef_tlm", "fight_mode_task");
    private static final String EPIC_WEAPON_CHECK_METHOD = "isWeaponCap";
    private static final Map<Class<?>, Optional<Method>> EPIC_WEAPON_CHECKS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> WARNED_EPIC_CLASSES = ConcurrentHashMap.newKeySet();

    public static boolean isWeapon(EntityMaid maid, IMaidTask task, ItemStack stack) {
        if (task == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (isEpicFightTask(task)) {
            return invokeEpicWeaponCheck(task, stack);
        }
        if (!(task instanceof IAttackTask attackTask)) {
            return false;
        }
        try {
            return attackTask.isWeapon(maid, stack);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Combat task {} failed while checking weapon {}",
                    safeTaskId(task), stack.getItem(), error);
            return false;
        }
    }

    public static FunctionCallSwitchResult prepareSwitch(EntityMaid maid, IMaidTask task) {
        if (!isEpicFightTask(task)) {
            return task.onFunctionCallSwitch(maid);
        }
        if (isWeapon(maid, task, maid.getMainHandItem())) {
            return FunctionCallSwitchResult.NO_CHANGE;
        }
        boolean equipped = TaskEquipUtil.tryEquipFromBackpack(maid,
                stack -> isWeapon(maid, task, stack));
        return equipped ? FunctionCallSwitchResult.OK : FunctionCallSwitchResult.MISSING_REQUIRED_ITEM;
    }

    static boolean isEpicFightTaskId(ResourceLocation taskId) {
        return EPIC_FIGHT_TASK.equals(taskId);
    }

    static boolean invokeEpicWeaponCheck(Object task, ItemStack stack) {
        if (task == null || stack == null || stack.isEmpty()) {
            return false;
        }
        Class<?> taskClass = task.getClass();
        Optional<Method> method = EPIC_WEAPON_CHECKS.computeIfAbsent(taskClass,
                CombatTaskCompatibility::findEpicWeaponCheck);
        if (method.isEmpty()) {
            warnEpicCompatibilityOnce(taskClass, null);
            return false;
        }
        try {
            Object result = method.get().invoke(task, stack);
            return result instanceof Boolean compatible && compatible;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warnEpicCompatibilityOnce(taskClass, error);
            return false;
        }
    }

    private static Optional<Method> findEpicWeaponCheck(Class<?> taskClass) {
        try {
            Method method = taskClass.getMethod(EPIC_WEAPON_CHECK_METHOD, ItemStack.class);
            Class<?> returnType = method.getReturnType();
            if (returnType != boolean.class && returnType != Boolean.class) {
                return Optional.empty();
            }
            return Optional.of(method);
        } catch (NoSuchMethodException | SecurityException error) {
            return Optional.empty();
        }
    }

    private static boolean isEpicFightTask(IMaidTask task) {
        try {
            return task != null && isEpicFightTaskId(task.getUid());
        } catch (Exception | LinkageError error) {
            return false;
        }
    }

    private static void warnEpicCompatibilityOnce(Class<?> taskClass, Throwable error) {
        if (!WARNED_EPIC_CLASSES.add(taskClass)) {
            return;
        }
        if (error == null) {
            TlmBeyondSpace.LOGGER.warn(
                    "Epic Fight maid task {} does not expose {}(ItemStack); its weapons cannot be selected",
                    taskClass.getName(), EPIC_WEAPON_CHECK_METHOD);
        } else {
            TlmBeyondSpace.LOGGER.warn(
                    "Epic Fight maid task {} failed during {}(ItemStack); its weapons cannot be selected",
                    taskClass.getName(), EPIC_WEAPON_CHECK_METHOD, error);
        }
    }

    private static String safeTaskId(IMaidTask task) {
        try {
            return task == null || task.getUid() == null ? "<unknown>" : task.getUid().toString();
        } catch (Exception | LinkageError ignored) {
            return "<unavailable>";
        }
    }

    private CombatTaskCompatibility() {
    }
}
