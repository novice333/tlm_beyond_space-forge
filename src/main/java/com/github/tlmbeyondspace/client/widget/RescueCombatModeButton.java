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
    private static final int LABEL_COLOR = 0x444444;
    private static final int DEFAULT_VALUE_COLOR = 0xFFFF55;
    private final int labelWidth;
    private final OnPress previousPress;
    private final OnPress nextPress;
    private Component displayedValue;
    private boolean previousClicked;

    public RescueCombatModeButton(int x, int y, Component label, Component value,
                                  OnPress previousPress, OnPress nextPress) {
        this(x, y, label, value, 82, previousPress, nextPress);
    }

    public RescueCombatModeButton(int x, int y, Component label, Component value, int labelWidth,
                                  OnPress previousPress, OnPress nextPress) {
        super(x, y, label, value, previousPress, nextPress);
        this.labelWidth = Math.max(48, Math.min(width - ARROW_WIDTH * 2 - 18, labelWidth));
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
        int oldDividerStart = getX() + 119;
        graphics.fill(getX() + labelWidth, getY(), oldDividerStart + 2, getY() + 1, 0xFFE0CA9F);
        graphics.fill(getX() + labelWidth, getY() + 1, oldDividerStart + 2, getY() + 12, 0xFFA09172);
        graphics.fill(getX() + labelWidth, getY() + 12, oldDividerStart + 2, getY() + 13, 0xFF544C3B);
        graphics.fill(getX() + labelWidth - 1, getY() + 1,
                getX() + labelWidth, getY() + 12, 0xFF544C3B);
        graphics.fill(getX() + labelWidth, getY(),
                getX() + labelWidth + 1, getY() + 12, 0xFFE0CA9F);

        graphics.enableScissor(getX() + 3, getY(), getX() + labelWidth - 2, getY() + height);
        graphics.drawString(minecraft.font, getMessage(), getX() + 5, getY() + 3,
                LABEL_COLOR, false);
        graphics.disableScissor();

        int valueColor = valueColor(displayedValue);
        int previousCenter = getX() + labelWidth + ARROW_WIDTH / 2;
        int nextCenter = getX() + width - ARROW_WIDTH / 2;
        graphics.drawCenteredString(minecraft.font, "◀", previousCenter, getY() + 3, valueColor);
        graphics.drawCenteredString(minecraft.font, "▶", nextCenter, getY() + 3, valueColor);

        int valueLeft = getX() + labelWidth + ARROW_WIDTH;
        int valueRight = getX() + width - ARROW_WIDTH;
        int valueX = valueLeft + (valueRight - valueLeft - minecraft.font.width(displayedValue)) / 2;
        graphics.enableScissor(valueLeft, getY(), valueRight, getY() + height);
        graphics.drawString(minecraft.font, displayedValue, valueX, getY() + 3,
                valueColor, false);
        graphics.disableScissor();
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
