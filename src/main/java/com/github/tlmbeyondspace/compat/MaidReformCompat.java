package com.github.tlmbeyondspace.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Optional MaidReform integration without linking any MaidReform class. */
public final class MaidReformCompat {
    private static final String MOD_ID = "maidreform";
    private static final String PLAYER_KNOCKDOWN_TAG = "isPlayerKnockDown";

    public static boolean isLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isPlayerKnockedDown(ServerPlayer player) {
        if (!isLoaded()) {
            return false;
        }
        try {
            return player.getPersistentData().getBoolean(PLAYER_KNOCKDOWN_TAG);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    private MaidReformCompat() {
    }
}
