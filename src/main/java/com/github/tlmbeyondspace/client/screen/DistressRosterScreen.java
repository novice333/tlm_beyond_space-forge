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
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DistressRosterScreen extends Screen {
    private static final int ROWS_PER_PAGE = 4;
    private static final int ROW_HEIGHT = 44;
    private final InteractionHand hand;
    private final List<WorkingEntry> entries = new ArrayList<>();
    private List<WorkingEntry> displayedEntries = List.of();
    private int page;
    private boolean recallMode;
    private final boolean maidReformAvailable;
    private boolean knockdownRescue;
    private String searchText = "";
    private RosterFilter filter = RosterFilter.ALL;
    private EditBox searchBox;
    private boolean pendingFilterRefresh;

    public DistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> source) {
        this(hand, source, false, false, false);
    }

    public DistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> source, boolean recallMode) {
        this(hand, source, recallMode, false, false);
    }

    public DistressRosterScreen(InteractionHand hand, List<MaidRosterEntry> source, boolean recallMode,
                                boolean maidReformAvailable, boolean knockdownRescue) {
        super(Component.translatable("screen.tlm_beyond_space.distress_roster"));
        this.hand = hand;
        source.forEach(entry -> entries.add(new WorkingEntry(entry)));
        this.recallMode = recallMode;
        this.maidReformAvailable = maidReformAvailable;
        this.knockdownRescue = knockdownRescue;
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        boolean restoreSearchFocus = searchBox != null && searchBox.isFocused();
        clearWidgets();
        int left = width / 2 - 175;
        int headerTop = Math.max(0, height / 2 - (maidReformAvailable ? 128 : 113));
        int top = headerTop + (maidReformAvailable ? 59 : 40);
        int controlsTop = top + ROWS_PER_PAGE * ROW_HEIGHT;
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

        if (maidReformAvailable) {
            addRenderableWidget(Button.builder(knockdownText(), ignored -> {
                knockdownRescue = !knockdownRescue;
                rebuildRows();
            }).bounds(left, headerTop + 35, 345, 20).build());
        }

        List<WorkingEntry> visible = visibleEntries();
        int maxPage = Math.max(0, (visible.size() - 1) / ROWS_PER_PAGE);
        page = Math.min(page, maxPage);
        int start = page * ROWS_PER_PAGE;
        displayedEntries = visible.subList(Math.min(start, visible.size()),
                Math.min(start + ROWS_PER_PAGE, visible.size()));
        for (int row = 0; row < displayedEntries.size(); row++) {
            WorkingEntry entry = displayedEntries.get(row);
            addRenderableWidget(Button.builder(responseModeText(entry), button -> {
                entry.cycleResponseMode();
                button.setMessage(enabledText(entry));
                if (filter == RosterFilter.ENABLED) {
                    pendingFilterRefresh = true;
                }
            }).bounds(left + 210, top + row * ROW_HEIGHT + 10, 135, 20).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            page--;
            rebuildRows();
        }).bounds(left, controlsTop, 30, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            page++;
            rebuildRows();
        }).bounds(left + 35, controlsTop, 30, 20).build());
        next.active = (page + 1) * ROWS_PER_PAGE < visible.size();
        addRenderableWidget(Button.builder(filterText(), ignored -> {
            filter = filter.next();
            page = 0;
            rebuildRows();
        }).bounds(left + 70, controlsTop, 115, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> saveAndClose())
                .bounds(left + 190, controlsTop, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left + 275, controlsTop, 75, 20).build());
    }

    private Component recallText() {
        return Component.translatable(recallMode
                ? "gui.tlm_beyond_space.recall_mode_yes"
                : "gui.tlm_beyond_space.recall_mode_no");
    }

    private Component enabledText(WorkingEntry entry) {
        return responseModeText(entry);
    }

    private Component responseModeText(WorkingEntry entry) {
        if (!entry.enabled) {
            return Component.translatable("gui.tlm_beyond_space.respond_no");
        }
        return Component.translatable(entry.loadUnloaded
                ? "gui.tlm_beyond_space.respond_with_loading"
                : "gui.tlm_beyond_space.respond_loaded_only");
    }

    private Component knockdownText() {
        return Component.translatable(knockdownRescue
                ? "gui.tlm_beyond_space.knockdown_rescue_yes"
                : "gui.tlm_beyond_space.knockdown_rescue_no");
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
                        entry.loadUnloaded, entry.legacyCombatTask))
                .toList();
        BeyondSpaceNetwork.CHANNEL.sendToServer(new SaveDistressRosterC2SPacket(hand, saved, recallMode,
                knockdownRescue));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - 175;
        int headerTop = Math.max(0, height / 2 - (maidReformAvailable ? 128 : 113));
        int top = headerTop + (maidReformAvailable ? 59 : 40);
        graphics.drawCenteredString(font, title, width / 2, headerTop, 0xFFFFFF);
        for (int row = 0; row < displayedEntries.size(); row++) {
            WorkingEntry entry = displayedEntries.get(row);
            int rowTop = top + row * ROW_HEIGHT;
            int panelColor = row % 2 == 0 ? 0xA018222D : 0xA00F171F;
            int accent = entry.storedInSoulSpell ? 0xFFB06CFF
                    : entry.enabled ? (entry.loadUnloaded ? 0xFF59B9FF : 0xFF75D48B) : 0xFF59636D;
            graphics.fill(left - 5, rowTop - 3, left + 350, rowTop + 39, panelColor);
            graphics.fill(left - 5, rowTop - 3, left - 2, rowTop + 39, accent);
            graphics.fill(left - 2, rowTop + 38, left + 350, rowTop + 39, 0x805C6873);
            graphics.fill(left + 204, rowTop + 2, left + 205, rowTop + 34, 0x806A7580);

            int color = entry.storedInSoulSpell ? 0xD79BFF
                    : entry.sameDimension ? 0xFFFFFF : entry.loaded ? 0xE0B050 : 0xA0A0A0;
            Component indexedName = Component.literal((page * ROWS_PER_PAGE + row + 1) + ". ")
                    .append(entry.name.copy());
            graphics.drawString(font, font.plainSubstrByWidth(indexedName.getString(), 198),
                    left, rowTop, color, false);
            Component state = entry.storedInSoulSpell
                    ? Component.translatable("gui.tlm_beyond_space.stored_in_soul_spell")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    : entry.sameDimension
                    ? Component.translatable("gui.tlm_beyond_space.same_dimension").withStyle(ChatFormatting.GREEN)
                    : entry.loaded
                    ? Component.translatable("gui.tlm_beyond_space.other_dimension").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.tlm_beyond_space.not_loaded").withStyle(ChatFormatting.GRAY);
            int stateColor = entry.storedInSoulSpell ? 0xD79BFF
                    : entry.sameDimension ? 0x70D989 : entry.loaded ? 0xE0B050 : 0xA0A0A0;
            graphics.drawString(font, font.plainSubstrByWidth(state.getString(), 142),
                    left, rowTop + 11, stateColor, false);
            if (entry.positionKnown) {
                Component location = Component.translatable("gui.tlm_beyond_space.last_contact",
                        entry.dimension, entry.lastPosition.getX(), entry.lastPosition.getY(),
                        entry.lastPosition.getZ());
                graphics.drawString(font, font.plainSubstrByWidth(location.getString(), 198), left, rowTop + 22,
                        0x707070, false);
            }
            if (entry.rescueOriginKnown) {
                Component origin = Component.translatable("gui.tlm_beyond_space.rescue_origin",
                        entry.rescueOriginDimension, entry.rescueOriginPosition.getX(),
                        entry.rescueOriginPosition.getY(), entry.rescueOriginPosition.getZ());
                graphics.drawString(font, font.plainSubstrByWidth(origin.getString(), 198), left,
                        rowTop + 32, 0xE0B050, false);
            }
            int order = enabledOrder(entry);
            if (order > 0) {
                int orderColor = order <= 20 ? 0x7FC8FF : 0x808080;
                graphics.drawString(font, Component.translatable("gui.tlm_beyond_space.response_order", order),
                        left + 145, rowTop + 11, orderColor, false);
            }
        }
        graphics.drawString(font, Component.translatable("screen.tlm_beyond_space.page", page + 1,
                        Math.max(1, (visibleEntries().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)),
                left + 72, top + ROWS_PER_PAGE * ROW_HEIGHT + 6, 0xA0A0A0, false);
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
        private final String dimension;
        private final boolean positionKnown;
        private final BlockPos lastPosition;
        private final boolean rescueOriginKnown;
        private final String rescueOriginDimension;
        private final BlockPos rescueOriginPosition;
        private boolean enabled;
        private boolean loadUnloaded;
        private final boolean storedInSoulSpell;
        private final ResourceLocation legacyCombatTask;

        private WorkingEntry(MaidRosterEntry source) {
            maidId = source.maidId();
            name = source.name();
            loaded = source.loaded();
            sameDimension = source.sameDimension();
            dimension = source.dimension();
            positionKnown = source.positionKnown();
            lastPosition = source.lastPosition();
            rescueOriginKnown = source.rescueOriginKnown();
            rescueOriginDimension = source.rescueOriginDimension();
            rescueOriginPosition = source.rescueOriginPosition();
            enabled = source.enabled();
            loadUnloaded = source.loadUnloaded();
            storedInSoulSpell = source.storedInSoulSpell();
            legacyCombatTask = source.combatTask();
        }

        private void cycleResponseMode() {
            if (!enabled) {
                enabled = true;
                loadUnloaded = false;
            } else if (!loadUnloaded) {
                loadUnloaded = true;
            } else {
                enabled = false;
                loadUnloaded = true;
            }
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
