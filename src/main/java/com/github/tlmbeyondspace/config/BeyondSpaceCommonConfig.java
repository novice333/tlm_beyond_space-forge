package com.github.tlmbeyondspace.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BeyondSpaceCommonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue RESCUE_RADIUS;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue HURT_MEMORY_TICKS;
    public static final ForgeConfigSpec.IntValue QUIET_TICKS;
    public static final ForgeConfigSpec.IntValue FAILURE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue SAFE_RETURN_RADIUS;
    public static final ForgeConfigSpec.IntValue SIGNAL_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue SIGNAL_MIN_ACTIVE_TICKS;
    public static final ForgeConfigSpec.IntValue SIGNAL_QUIET_TICKS;
    public static final ForgeConfigSpec.IntValue SIGNAL_MAX_ACTIVE_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_SIGNAL_HELPERS;
    public static final ForgeConfigSpec.BooleanValue PROMAID_AUTO_DISABLE_DIMENSION_FOLLOW;
    public static final ForgeConfigSpec.BooleanValue PROMAID_LOW_HEALTH_PRIORITY;
    public static final ForgeConfigSpec.BooleanValue PROMAID_OWNER_DEATH_HANDOFF;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("rescue");
        RESCUE_RADIUS = builder.comment("Threat scan radius around the maid and, in bond mode, her owner.")
                .defineInRange("rescueRadius", 12, 1, 64);
        SCAN_INTERVAL_TICKS = builder.comment("Ticks between passive threat scans.")
                .defineInRange("scanIntervalTicks", 10, 1, 200);
        HURT_MEMORY_TICKS = builder.comment("How long a valid attacker can trigger rescue after a hurt event.")
                .defineInRange("hurtMemoryTicks", 40, 1, 1200);
        QUIET_TICKS = builder.comment("Continuous safe ticks required before restoring the original task.")
                .defineInRange("quietTicks", 40, 1, 1200);
        FAILURE_COOLDOWN_TICKS = builder.comment("Cooldown after a failed rescue transition.")
                .defineInRange("failureCooldownTicks", 100, 1, 6000);
        SAFE_RETURN_RADIUS = builder.comment("Radius used to find a safe block near the recorded position.")
                .defineInRange("safeReturnRadius", 2, 0, 16);
        builder.pop();

        builder.push("distressSignal");
        SIGNAL_COOLDOWN_TICKS = builder.comment("Cooldown after using a distress signal.")
                .defineInRange("cooldownTicks", 100, 1, 12000);
        SIGNAL_MIN_ACTIVE_TICKS = builder.comment("Minimum duration of a distress rescue, even if no threat is found.")
                .defineInRange("minimumActiveTicks", 100, 0, 12000);
        SIGNAL_QUIET_TICKS = builder.comment("Continuous safe ticks required before a distress rescue returns.")
                .defineInRange("quietTicks", 100, 1, 1200);
        SIGNAL_MAX_ACTIVE_TICKS = builder.comment("Maximum duration of one distress rescue.")
                .defineInRange("maximumActiveTicks", 6000, 20, 72000);
        MAX_SIGNAL_HELPERS = builder.comment("Maximum loaded maids summoned by one signal.")
                .defineInRange("maxHelpers", 20, 1, 20);
        builder.pop();

        builder.push("compatibility");
        builder.push("promaid");
        PROMAID_AUTO_DISABLE_DIMENSION_FOLLOW = builder.comment(
                        "When Promaid is installed, disable its dimension-follow switch in memory at server startup.",
                        "This mod does not save Promaid's config file or require Promaid as a dependency.")
                .define("autoDisableDimensionFollow", true);
        PROMAID_LOW_HEALTH_PRIORITY = builder.comment(
                        "Let Promaid self-preservation interrupt or reject Beyond Space rescue sessions.")
                .define("lowHealthPriority", true);
        PROMAID_OWNER_DEATH_HANDOFF = builder.comment(
                        "When Promaid's owner-death teleport is enabled, only release distress state and let Promaid teleport.")
                .define("ownerDeathHandoff", true);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    private BeyondSpaceCommonConfig() {
    }
}
