package com.github.tlmbeyondspace.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.client.ClientSetup;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import com.github.tlmbeyondspace.service.CombatMaidBookService;
import com.github.tlmbeyondspace.service.MaidRosterService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

public final class SpacetimeRescueCharmItem extends Item {
    public SpacetimeRescueCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                CombatMaidBookService.resetAllToForbidden(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientSetup.openTaskModeScreen(hand, stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (!context.getLevel().isClientSide
                    && context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                CombatMaidBookService.resetAllToForbidden(serverPlayer);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!player.level().isClientSide
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                if (target instanceof EntityMaid maid) {
                    MaidRosterService.observe(maid);
                }
                CombatMaidBookService.resetAllToForbidden(serverPlayer);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!(target instanceof EntityMaid maid)) {
            return InteractionResult.PASS;
        }
        if (!maid.isOwnedBy(player)) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.tlm_beyond_space.not_owner"), true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!player.level().isClientSide) {
            CombatMaidBookService.bind(maid, (net.minecraft.server.level.ServerPlayer) player, stack);
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.binding_saved",
                    maid.getDisplayName()), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.spacetime_rescue_charm.flavor")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.spacetime_rescue_charm.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.spacetime_rescue_charm.2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tlm_beyond_space.spacetime_rescue_charm.3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
