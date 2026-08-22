package com.github.tlmbeyondspace.client.screen;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.network.packet.SaveCharmProfileC2SPacket;
import com.github.tlmbeyondspace.service.RescueTaskClassifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TaskModeConfigScreen extends Screen {
    private static final int ROWS_PER_PAGE = 7;
    private final InteractionHand hand;
    private final TaskModeProfile profile;
    private final List<IMaidTask> tasks = new ArrayList<>();
    private final List<Button> modeButtons = new ArrayList<>();
    private int page;
    private Button previousButton;
    private Button nextButton;

    public TaskModeConfigScreen(InteractionHand hand, TaskModeProfile profile) {
        super(Component.translatable("screen.tlm_beyond_space.task_modes"));
        this.hand = hand;
        this.profile = profile.copy();
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            if (RescueTaskClassifier.isSourceTask(task)) {
                tasks.add(task);
            }
        }
        Comparator<IMaidTask> byUid = Comparator.comparing(task -> task.getUid().toString());
        tasks.sort(byUid);
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        clearWidgets();
        modeButtons.clear();
        int left = width / 2 - 195;
        int top = height / 2 - 58;
        int start = page * ROWS_PER_PAGE;

        for (int row = 0; row < ROWS_PER_PAGE && start + row < tasks.size(); row++) {
            IMaidTask task = tasks.get(start + row);
            Button button = Button.builder(profile.get(task.getUid()).displayName(), ignored -> {
                ResourceLocation taskId = task.getUid();
                profile.set(taskId, profile.get(taskId).next());
                refreshRowLabels();
            }).bounds(left + 275, top + row * 22, 110, 20).build();
            addRenderableWidget(button);
            modeButtons.add(button);
        }

        int navigationTop = top + 160;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            page--;
            rebuildRows();
        }).bounds(left, navigationTop, 30, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            page++;
            rebuildRows();
        }).bounds(left + 35, navigationTop, 30, 20).build());
        previousButton.active = page > 0;
        nextButton.active = (page + 1) * ROWS_PER_PAGE < tasks.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> saveAndClose())
                .bounds(left + 230, navigationTop, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left + 310, navigationTop, 75, 20).build());
    }

    private void refreshRowLabels() {
        int start = page * ROWS_PER_PAGE;
        for (int index = 0; index < modeButtons.size() && start + index < tasks.size(); index++) {
            IMaidTask task = tasks.get(start + index);
            modeButtons.get(index).setMessage(profile.get(task.getUid()).displayName());
        }
    }

    private void saveAndClose() {
        BeyondSpaceNetwork.CHANNEL.sendToServer(new SaveCharmProfileC2SPacket(hand, profile.save()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - 195;
        int top = height / 2 - 58;
        graphics.drawCenteredString(font, title, width / 2, top - 35, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.tlm_beyond_space.column.source_task"),
                left, top - 11, 0xA0A0A0, false);
        graphics.drawString(font, Component.translatable("screen.tlm_beyond_space.column.rescue_mode"),
                left + 279, top - 11, 0xA0A0A0, false);
        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < tasks.size(); row++) {
            graphics.drawString(font, tasks.get(start + row).getName(), left, top + row * 22 + 6,
                    0xFFFFFF, false);
        }
        graphics.drawString(font, Component.translatable("screen.tlm_beyond_space.page", page + 1,
                        Math.max(1, (tasks.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)),
                left + 75, top + 166, 0xA0A0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
