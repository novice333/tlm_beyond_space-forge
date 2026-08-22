package com.github.tlmbeyondspace.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum RescueMode {
    FORBIDDEN,
    SOLO,
    BOND;

    public RescueMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public RescueMode previous() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }

    public Component displayName() {
        Component name = Component.translatable("mode.tlm_beyond_space." + name().toLowerCase());
        return switch (this) {
            case FORBIDDEN -> name.copy().withStyle(ChatFormatting.RED);
            case SOLO -> name.copy().withStyle(ChatFormatting.YELLOW);
            case BOND -> name.copy().withStyle(ChatFormatting.GREEN);
        };
    }
}
