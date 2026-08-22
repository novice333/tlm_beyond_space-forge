package com.github.tlmbeyondspace.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BeyondSpaceClientConfig {
    public static final String LEGACY_DISTRESS_SIGNAL_DESCRIPTION = "帮帮我，女仆大人";
    public static final String DEFAULT_DISTRESS_SIGNAL_DESCRIPTION = "帮帮我女仆小姐";
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> DISTRESS_SIGNAL_DESCRIPTION;
    public static final ForgeConfigSpec.BooleanValue SHOW_TASK_UID;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        DISTRESS_SIGNAL_DESCRIPTION = builder
                .comment("Tooltip shown on the distress signal item.")
                .define("distressSignalDescription", DEFAULT_DISTRESS_SIGNAL_DESCRIPTION);
        SHOW_TASK_UID = builder.comment("Show task identifiers in task selection screens.")
                .define("showTaskUid", true);
        SPEC = builder.build();
    }

    private BeyondSpaceClientConfig() {
    }

    public static String getDistressSignalDescription() {
        String configured = DISTRESS_SIGNAL_DESCRIPTION.get();
        return LEGACY_DISTRESS_SIGNAL_DESCRIPTION.equals(configured)
                ? DEFAULT_DISTRESS_SIGNAL_DESCRIPTION : configured;
    }
}
