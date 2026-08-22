package com.github.tlmbeyondspace.item;

import com.github.tlmbeyondspace.config.BeyondSpaceClientConfig;
import com.github.tlmbeyondspace.data.DistressSignalData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.github.tlmbeyondspace.service.DistressSignalService;
import com.github.tlmbeyondspace.service.DistressRecallService;
import com.github.tlmbeyondspace.service.MaidRosterService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class DistressSignalItem extends Item {
    public DistressSignalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                MaidRosterService.openRoster(serverPlayer, hand, stack);
            } else if (DistressSignalData.fromItem(stack).recallMode()) {
                DistressRecallService.recallForOwner(serverPlayer, true);
            } else {
                DistressSignalService.activate(serverPlayer, hand, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(BeyondSpaceClientConfig.getDistressSignalDescription())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("            ")
                .append(Component.translatable("tooltip.tlm_beyond_space.distress_signal.reply"))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.distress_signal.usage")
                .withStyle(ChatFormatting.GRAY));
    }
}
