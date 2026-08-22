package com.github.tlmbeyondspace.client.widget;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.MaidConfigButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RescueCombatModeButton extends MaidConfigButton {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "touhou_little_maid", "textures/gui/maid_gui_button.png");
    private static final int ARROW_WIDTH = 12;
    private static final int ORIGINAL_DIVIDER_OFFSET = 119;
    private static final int ORIGINAL_LEFT_ARROW_U = 183;
    private static final int ORIGINAL_RIGHT_ARROW_U = 217;
    private static final int ORIGINAL_ARROW_WIDTH = 10;
    private static final int HOVER_TEXTURE_V = 141;
    private static final int LABEL_COLOR = 0x444444;
    private static final int DEFAULT_VALUE_COLOR = 0xFFFF55;
    private static final int NORMAL_TOP_COLOR = 0xFFE0CA9F;
    private static final int NORMAL_BODY_COLOR = 0xFFA09172;
    private static final int NORMAL_BOTTOM_COLOR = 0xFF544C3B;
    private static final int HOVER_TOP_COLOR = 0xFF3B91AF;
    private static final int HOVER_BODY_COLOR = 0xFF2B6E86;
    private static final int HOVER_BOTTOM_COLOR = 0xFF15323B;
    private final int labelWidth;
    private final boolean pageNavigation;
    private final OnPress previousPress;
    private final OnPress nextPress;
    private Component displayedValue;
    private boolean previousClicked;

    public RescueCombatModeButton(int x, int y, Component label, Component value,
                                  OnPress previousPress, OnPress nextPress) {
        this(x, y, label, value, 82, false, previousPress, nextPress);
    }

    public RescueCombatModeButton(int x, int y, Component label, Component value, int labelWidth,
                                  OnPress previousPress, OnPress nextPress) {
        this(x, y, label, value, labelWidth, false, previousPress, nextPress);
    }

    public RescueCombatModeButton(int x, int y, Component label, Component value, int labelWidth,
                                  boolean pageNavigation,
                                  OnPress previousPress, OnPress nextPress) {
        super(x, y, label, value, previousPress, nextPress);
        this.pageNavigation = pageNavigation;
        this.labelWidth = pageNavigation
                ? 0
                : Math.max(48, Math.min(width - ARROW_WIDTH * 2 - 18, labelWidth));
        this.previousPress = previousPress;
        this.nextPress = nextPress;
        displayedValue = value;
    }

    @Override
    public void setValue(Component value) {
        super.setValue(value);
        displayedValue = value;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.enableDepthTest();

        // Recompose the original TLM button so the value area is 82 px instead of 44 px.
        graphics.blit(TEXTURE, getX(), getY(), 63, 128, width, height, 256, 256);
        if (pageNavigation) {
            eraseOriginalDivider(graphics);
        } else {
            relocateDivider(graphics);
        }

        boolean hovered = !pageNavigation
                && mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + height;
        if (hovered) {
            drawOriginalStyleHover(graphics);
        }

        if (!pageNavigation) {
            graphics.enableScissor(getX() + 3, getY(), getX() + labelWidth - 2, getY() + height);
            graphics.drawString(minecraft.font, getMessage(), getX() + 5, getY() + 3,
                    LABEL_COLOR, false);
            graphics.disableScissor();
        }

        int valueColor = valueColor(displayedValue);
        int previousCenter = getX() + labelWidth + ARROW_WIDTH / 2;
        int nextCenter = getX() + width - ARROW_WIDTH / 2;
        if (pageNavigation) {
            graphics.drawCenteredString(minecraft.font, "◀", previousCenter, getY() + 3,
                    DEFAULT_VALUE_COLOR);
            graphics.drawCenteredString(minecraft.font, "▶", nextCenter, getY() + 3,
                    DEFAULT_VALUE_COLOR);
        }

        int valueLeft = getX() + labelWidth + ARROW_WIDTH;
        int valueRight = getX() + width - ARROW_WIDTH;
        int valueX = valueLeft + (valueRight - valueLeft - minecraft.font.width(displayedValue)) / 2;
        graphics.enableScissor(valueLeft, getY(), valueRight, getY() + height);
        graphics.drawString(minecraft.font, displayedValue, valueX, getY() + 3,
                valueColor, false);
        graphics.disableScissor();
    }

    private void relocateDivider(GuiGraphics graphics) {
        int oldDividerStart = getX() + ORIGINAL_DIVIDER_OFFSET;
        graphics.fill(getX() + labelWidth, getY(), oldDividerStart + 2, getY() + 1,
                NORMAL_TOP_COLOR);
        graphics.fill(getX() + labelWidth, getY() + 1, oldDividerStart + 2, getY() + 12,
                NORMAL_BODY_COLOR);
        graphics.fill(getX() + labelWidth, getY() + 12, oldDividerStart + 2, getY() + 13,
                NORMAL_BOTTOM_COLOR);
        graphics.fill(getX() + labelWidth - 1, getY() + 1,
                getX() + labelWidth, getY() + 12, NORMAL_BOTTOM_COLOR);
        graphics.fill(getX() + labelWidth, getY(),
                getX() + labelWidth + 1, getY() + 12, NORMAL_TOP_COLOR);
    }

    private void eraseOriginalDivider(GuiGraphics graphics) {
        int divider = getX() + ORIGINAL_DIVIDER_OFFSET;
        graphics.fill(divider, getY(), divider + 2, getY() + 1, NORMAL_TOP_COLOR);
        graphics.fill(divider, getY() + 1, divider + 2, getY() + 12, NORMAL_BODY_COLOR);
        graphics.fill(divider, getY() + 12, divider + 2, getY() + 13, NORMAL_BOTTOM_COLOR);
    }

    private void drawOriginalStyleHover(GuiGraphics graphics) {
        int valueStart = getX() + labelWidth;
        graphics.fill(valueStart, getY(), getX() + width - 1, getY() + 1, HOVER_TOP_COLOR);
        graphics.fill(valueStart, getY() + 1, getX() + width - 1, getY() + 12,
                HOVER_BODY_COLOR);
        graphics.fill(valueStart, getY() + 12, getX() + width - 1, getY() + 13,
                HOVER_BOTTOM_COLOR);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height,
                HOVER_BOTTOM_COLOR);

        graphics.blit(TEXTURE, valueStart + 1, getY(), ORIGINAL_LEFT_ARROW_U, HOVER_TEXTURE_V,
                ORIGINAL_ARROW_WIDTH, height, 256, 256);
        graphics.blit(TEXTURE, getX() + width - ORIGINAL_ARROW_WIDTH - 1, getY(),
                ORIGINAL_RIGHT_ARROW_U, HOVER_TEXTURE_V,
                ORIGINAL_ARROW_WIDTH, height, 256, 256);
    }

    private static int valueColor(Component value) {
        return value.getStyle().getColor() == null
                ? DEFAULT_VALUE_COLOR
                : value.getStyle().getColor().getValue();
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        boolean inside = active && visible
                && mouseX >= getX() && mouseX <= getX() + width
                && mouseY >= getY() && mouseY <= getY() + getHeight();
        if (inside) {
            previousClicked = mouseX >= getX() + labelWidth
                    && mouseX < getX() + labelWidth + ARROW_WIDTH;
        }
        return inside;
    }

    @Override
    public void onPress() {
        if (previousClicked) {
            previousPress.onPress(this);
        } else {
            nextPress.onPress(this);
        }
    }
}
