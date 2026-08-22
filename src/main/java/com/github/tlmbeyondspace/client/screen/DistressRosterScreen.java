package com.github.tlmbeyondspace.client.screen;

import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.network.packet.SaveDistressRosterC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DistressRosterScreen extends Screen {
    private static final int ROWS_PER_PAGE = 6;
    private final InteractionHand hand;
    private final List<WorkingEntry> entries = new ArrayList<>();
    private List<WorkingEntry> displayedEntries = List.of();
    private int page;
    private boolean recallMode;
    private String searchText = "";
    private RosterFilter filter = RosterFilter.ALL;
    private EditBox searchBox;
    private boolean pendingFilterRefresh;

    public DistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> source) {
        this(hand, source, false);
    }

    public DistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> source, boolean recallMode) {
        super(Component.translatable("screen.tlm_beyond_space.distress_roster"));
        this.hand = hand;
        source.forEach(entry -> entries.add(new WorkingEntry(entry)));
        this.recallMode = recallMode;
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        boolean restoreSearchFocus = searchBox != null && searchBox.isFocused();
        clearWidgets();
        int left = width / 2 - 175;
        int headerTop = Math.max(6, height / 2 - 102);
        int top = headerTop + 40;
        addRenderableWidget(Button.builder(recallText(), ignored -> {
            recallMode = !recallMode;
            rebuildRows();
        }).bounds(left, headerTop + 15, 190, 20).build());

        searchBox = new EditBox(font, left + 195, headerTop + 15, 150, 20,
                Component.translatable("gui.tlm_beyond_space.search_maids"));
        searchBox.setHint(Component.translatable("gui.tlm_beyond_space.search_maids"));
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            page = 0;
            pendingFilterRefresh = true;
        });
        addRenderableWidget(searchBox);
        if (restoreSearchFocus) {
            setFocused(searchBox);
            searchBox.setCursorPosition(searchText.length());
        }

        List<WorkingEntry> visible = visibleEntries();
        int maxPage = Math.max(0, (visible.size() - 1) / ROWS_PER_PAGE);
        page = Math.min(page, maxPage);
        int start = page * ROWS_PER_PAGE;
        displayedEntries = visible.subList(Math.min(start, visible.size()),
                Math.min(start + ROWS_PER_PAGE, visible.size()));
        for (int row = 0; row < displayedEntries.size(); row++) {
            WorkingEntry entry = displayedEntries.get(row);
            addRenderableWidget(Button.builder(enabledText(entry), button -> {
                entry.enabled = !entry.enabled;
                button.setMessage(enabledText(entry));
                if (filter == RosterFilter.ENABLED) {
                    pendingFilterRefresh = true;
                }
            }).bounds(left + 235, top + row * 24, 110, 20).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            page--;
            rebuildRows();
        }).bounds(left, top + 150, 30, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            page++;
            rebuildRows();
        }).bounds(left + 35, top + 150, 30, 20).build());
        next.active = (page + 1) * ROWS_PER_PAGE < visible.size();
        addRenderableWidget(Button.builder(filterText(), ignored -> {
            filter = filter.next();
            page = 0;
            rebuildRows();
        }).bounds(left + 70, top + 150, 115, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> saveAndClose())
                .bounds(left + 190, top + 150, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left + 275, top + 150, 75, 20).build());
    }

    private Component recallText() {
        return Component.translatable(recallMode
                ? "gui.tlm_beyond_space.recall_mode_yes"
                : "gui.tlm_beyond_space.recall_mode_no");
    }

    private Component enabledText(WorkingEntry entry) {
        return Component.translatable(entry.enabled
                ? "gui.tlm_beyond_space.respond_yes"
                : "gui.tlm_beyond_space.respond_no");
    }

    private Component filterText() {
        return Component.translatable(filter.translationKey);
    }

    private List<WorkingEntry> visibleEntries() {
        String query = searchText.strip().toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(filter::matches)
                .filter(entry -> query.isEmpty()
                        || entry.name.getString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private int enabledOrder(WorkingEntry target) {
        int order = 0;
        for (WorkingEntry entry : entries) {
            if (entry.enabled) {
                order++;
                if (entry == target) {
                    return order;
                }
            }
        }
        return 0;
    }

    private void saveAndClose() {
        List<SaveDistressRosterC2SPacket.Entry> saved = entries.stream()
                .map(entry -> new SaveDistressRosterC2SPacket.Entry(entry.maidId, entry.enabled,
                        entry.legacyCombatTask))
                .toList();
        BeyondSpaceNetwork.CHANNEL.sendToServer(new SaveDistressRosterC2SPacket(hand, saved, recallMode));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - 175;
        int headerTop = Math.max(6, height / 2 - 102);
        int top = headerTop + 40;
        graphics.drawCenteredString(font, title, width / 2, headerTop, 0xFFFFFF);
        for (int row = 0; row < displayedEntries.size(); row++) {
            WorkingEntry entry = displayedEntries.get(row);
            int color = entry.sameDimension ? 0xFFFFFF : entry.loaded ? 0xE0B050 : 0x808080;
            graphics.drawString(font, entry.name, left, top + row * 24 + 2, color, false);
            Component state = entry.sameDimension
                    ? Component.translatable("gui.tlm_beyond_space.same_dimension").withStyle(ChatFormatting.GREEN)
                    : entry.loaded
                    ? Component.translatable("gui.tlm_beyond_space.other_dimension").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.tlm_beyond_space.not_loaded").withStyle(ChatFormatting.GRAY);
            graphics.drawString(font, state, left, top + row * 24 + 12, 0xA0A0A0, false);
            int order = enabledOrder(entry);
            if (order > 0) {
                int orderColor = order <= 20 ? 0x7FC8FF : 0x808080;
                graphics.drawString(font, Component.translatable("gui.tlm_beyond_space.response_order", order),
                        left + 180, top + row * 24 + 12, orderColor, false);
            }
        }
        graphics.drawString(font, Component.translatable("screen.tlm_beyond_space.page", page + 1,
                        Math.max(1, (visibleEntries().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)),
                left + 72, top + 156, 0xA0A0A0, false);
        if (displayedEntries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.tlm_beyond_space.no_maids"),
                    width / 2, height / 2, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingFilterRefresh) {
            pendingFilterRefresh = false;
            rebuildRows();
        }
    }

    private static final class WorkingEntry {
        private final UUID maidId;
        private final Component name;
        private final boolean loaded;
        private final boolean sameDimension;
        private boolean enabled;
        private final ResourceLocation legacyCombatTask;

        private WorkingEntry(MaidRosterEntry source) {
            maidId = source.maidId();
            name = source.name();
            loaded = source.loaded();
            sameDimension = source.sameDimension();
            enabled = source.enabled();
            legacyCombatTask = source.combatTask();
        }
    }

    private enum RosterFilter {
        ALL("gui.tlm_beyond_space.filter_all"),
        ENABLED("gui.tlm_beyond_space.filter_enabled"),
        LOADED("gui.tlm_beyond_space.filter_loaded"),
        SAME_DIMENSION("gui.tlm_beyond_space.filter_same_dimension"),
        OTHER_DIMENSION("gui.tlm_beyond_space.filter_other_dimension"),
        UNLOADED("gui.tlm_beyond_space.filter_unloaded");

        private final String translationKey;

        RosterFilter(String translationKey) {
            this.translationKey = translationKey;
        }

        private boolean matches(WorkingEntry entry) {
            return switch (this) {
                case ALL -> true;
                case ENABLED -> entry.enabled;
                case LOADED -> entry.loaded;
                case SAME_DIMENSION -> entry.loaded && entry.sameDimension;
                case OTHER_DIMENSION -> entry.loaded && !entry.sameDimension;
                case UNLOADED -> !entry.loaded;
            };
        }

        private RosterFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
