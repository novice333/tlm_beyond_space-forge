package com.github.tlmbeyondspace.client.widget;

import com.github.tartaricacid.touhoulittlemaid.api.client.gui.ITooltipButton;
import com.github.tlmbeyondspace.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RescueCombatTabButton extends Button implements ITooltipButton {
    private static final ResourceLocation SIDE = new ResourceLocation(
            "touhou_little_maid", "textures/gui/maid_gui_side.png");
    private static final int TAB_X = 194;
    private static final int TAB_Y = 5;
    private static final int SELECTED_BG_U = 107;
    private final ItemStack icon = ModItems.SPACETIME_RESCUE_CHARM.get().getDefaultInstance();
    private final List<Component> tooltips = List.of(
            Component.translatable("gui.tlm_beyond_space.rescue_combat_tab"),
            Component.translatable("gui.tlm_beyond_space.rescue_combat_tab.desc"));

    public RescueCombatTabButton(int leftPos, int topPos, boolean active, OnPress onPress) {
        super(Button.builder(Component.empty(), onPress)
                .bounds(leftPos + TAB_X, topPos + TAB_Y, 24, 26));
        this.active = active;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableDepthTest();
        if (!active) {
            graphics.blit(SIDE, getX(), getY(), SELECTED_BG_U, 21, width, height, 256, 256);
        }
        graphics.renderItem(icon, getX() + 4, getY() + 6);
    }

    @Override
    public boolean isTooltipHovered() {
        return active && isHovered();
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        graphics.renderComponentTooltip(minecraft.font, tooltips, mouseX, mouseY);
    }
}
