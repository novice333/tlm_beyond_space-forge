package com.github.tlmbeyondspace.client;

import com.github.tlmbeyondspace.client.screen.TaskModeConfigScreen;
import com.github.tlmbeyondspace.client.screen.DistressRosterScreen;
import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ClientSetup {
    public static void openTaskModeScreen(InteractionHand hand, ItemStack stack) {
        Minecraft.getInstance().setScreen(new TaskModeConfigScreen(hand, TaskModeProfile.fromItem(stack)));
    }

    public static void openDistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> entries) {
        Minecraft.getInstance().setScreen(new DistressRosterScreen(hand, entries));
    }

    public static void openDistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> entries,
                                                boolean recallMode) {
        Minecraft.getInstance().setScreen(new DistressRosterScreen(hand, entries, recallMode));
    }

    private ClientSetup() {
    }
}
