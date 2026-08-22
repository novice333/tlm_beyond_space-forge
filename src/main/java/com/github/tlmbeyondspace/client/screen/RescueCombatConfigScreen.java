package com.github.tlmbeyondspace.client.screen;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.MaidConfigButton;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.client.widget.RescueCombatModeButton;
import com.github.tlmbeyondspace.client.widget.RescueCombatTabButton;
import com.github.tlmbeyondspace.data.MaidCombatPreferenceData;
import com.github.tlmbeyondspace.data.MaidRescueProfileData;
import com.github.tlmbeyondspace.data.RescueMode;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import com.github.tlmbeyondspace.inventory.RescueCombatConfigMenu;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.network.packet.SaveMaidCombatPreferenceC2SPacket;
import com.github.tlmbeyondspace.network.packet.SaveMaidRescueProfileC2SPacket;
import com.github.tlmbeyondspace.service.RescueTaskClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Comparator;
import java.util.List;

public final class RescueCombatConfigScreen extends AbstractMaidContainerGui<RescueCombatConfigMenu> {
    private static final int ROWS_PER_PAGE = 5;
    private static final int SOURCE_LABEL_WIDTH = 104;
    private static final int PAGE_BACKGROUND_COLOR = 0xFFB7AA86;
    private static final int HEADER_BACKGROUND_COLOR = 0xFFAA9B76;
    private static final int PANEL_BORDER_COLOR = 0xFFD8C38F;

    private List<IMaidTask> combatTasks = List.of();
    private List<IMaidTask> sourceTasks = List.of();
    private TaskModeProfile profile = new TaskModeProfile();
    private int selectedIndex;
    private int page;
    private boolean dataInitialized;

    public RescueCombatConfigScreen(RescueCombatConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void initAdditionData() {
        if (!dataInitialized) {
            Comparator<IMaidTask> byUid = Comparator.comparing(task -> task.getUid().toString());
            combatTasks = TaskManager.getTaskIndex().stream()
                    .filter(RescueTaskClassifier::isCombatTask)
                    .sorted(byUid)
                    .toList();
            sourceTasks = TaskManager.getTaskIndex().stream()
                    .filter(RescueTaskClassifier::isSourceTask)
                    .sorted(byUid)
                    .toList();
            selectedIndex = findSelectedIndex(combatTasks,
                    MaidCombatPreferenceData.getPreferredTaskId(maid));
            MaidRescueProfileData.Data saved = MaidRescueProfileData.get(maid);
            profile = saved.profile() == null ? new TaskModeProfile() : saved.profile().copy();
            dataInitialized = true;
        }
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    @Override
    protected void initAdditionWidgets() {
        addRenderableWidget(new RescueCombatTabButton(leftPos, topPos, false, pressed -> {
        }));
        boolean editable = Minecraft.getInstance().player != null
                && maid.isOwnedBy(Minecraft.getInstance().player);

        MaidConfigButton combatSelector = new RescueCombatModeButton(
                leftPos + 86,
                topPos + 52,
                Component.translatable("gui.tlm_beyond_space.maid_config.combat_task.label"),
                selectedTaskName(),
                button -> cycleCombat(button, -1),
                button -> cycleCombat(button, 1));
        combatSelector.active = editable && !combatTasks.isEmpty();
        addRenderableWidget(combatSelector);

        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < sourceTasks.size(); row++) {
            IMaidTask task = sourceTasks.get(start + row);
            MaidConfigButton modeSelector = new RescueCombatModeButton(
                    leftPos + 86,
                    topPos + 68 + row * 15,
                    taskDisplayName(task),
                    profile.get(task.getUid()).displayName(),
                    SOURCE_LABEL_WIDTH,
                    button -> cycleSourceMode(button, task, -1),
                    button -> cycleSourceMode(button, task, 1));
            modeSelector.active = editable;
            addRenderableWidget(modeSelector);
        }

        MaidConfigButton pageSelector = new RescueCombatModeButton(
                leftPos + 86,
                topPos + 145,
                Component.translatable("gui.tlm_beyond_space.rescue_combat_page"),
                Component.literal((page + 1) + " / " + pageCount()),
                button -> changePage(-1),
                button -> changePage(1));
        pageSelector.active = pageCount() > 1;
        addRenderableWidget(pageSelector);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        // Recolor only this tab's content panel; preserve its frame and the inventory below.
        graphics.fill(leftPos + 82, topPos + 31, leftPos + 253, topPos + 162,
                PAGE_BACKGROUND_COLOR);
        graphics.fill(leftPos + 86, topPos + 35, leftPos + 250, topPos + 50,
                HEADER_BACKGROUND_COLOR);
        graphics.fill(leftPos + 86, topPos + 35, leftPos + 250, topPos + 36,
                PANEL_BORDER_COLOR);
        graphics.fill(leftPos + 86, topPos + 49, leftPos + 250, topPos + 50,
                PANEL_BORDER_COLOR);
        graphics.fill(leftPos + 86, topPos + 35, leftPos + 87, topPos + 50,
                PANEL_BORDER_COLOR);
        graphics.fill(leftPos + 249, topPos + 35, leftPos + 250, topPos + 50,
                PANEL_BORDER_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawCenteredString(font,
                Component.translatable("gui.tlm_beyond_space.rescue_combat_title"),
                168, 39, 0xFFFFFFFF);
    }

    private void cycleCombat(MaidConfigButton button, int direction) {
        if (combatTasks.isEmpty()) {
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + direction, combatTasks.size());
        IMaidTask selected = combatTasks.get(selectedIndex);
        button.setValue(taskDisplayName(selected));
        BeyondSpaceNetwork.CHANNEL.sendToServer(
                new SaveMaidCombatPreferenceC2SPacket(maid.getUUID(), selected.getUid()));
    }

    private void cycleSourceMode(MaidConfigButton button, IMaidTask task, int direction) {
        ResourceLocation taskId = task.getUid();
        RescueMode current = profile.get(taskId);
        RescueMode selected = direction < 0 ? current.previous() : current.next();
        profile.set(taskId, selected);
        button.setValue(selected.displayName());
        BeyondSpaceNetwork.CHANNEL.sendToServer(
                new SaveMaidRescueProfileC2SPacket(maid.getUUID(), profile.save()));
    }

    private void changePage(int direction) {
        if (pageCount() <= 1) {
            return;
        }
        page = Math.floorMod(page + direction, pageCount());
        init();
    }

    private int pageCount() {
        return Math.max(1, (sourceTasks.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private Component selectedTaskName() {
        return combatTasks.isEmpty()
                ? Component.translatable("mode.tlm_beyond_space.combat_missing")
                : taskDisplayName(combatTasks.get(selectedIndex));
    }

    private static Component taskDisplayName(IMaidTask task) {
        Component name = task.getName();
        if (name.getContents() instanceof TranslatableContents translated
                && !I18n.exists(translated.getKey())) {
            if (translated.getKey().startsWith("task.eclipticseasons.clea")) {
                return Component.literal("清理积雪");
            }
            return Component.literal(task.getUid().getPath().replace('_', ' ').replace('-', ' '));
        }
        return name;
    }

    private static int findSelectedIndex(List<IMaidTask> combatTasks, ResourceLocation selectedTask) {
        for (int index = 0; index < combatTasks.size(); index++) {
            if (combatTasks.get(index).getUid().equals(selectedTask)) {
                return index;
            }
        }
        return 0;
    }
}
