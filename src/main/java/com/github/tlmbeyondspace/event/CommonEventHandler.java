package com.github.tlmbeyondspace.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tlmbeyondspace.compat.PromaidCompat;
import com.github.tlmbeyondspace.service.DamageSignalService;
import com.github.tlmbeyondspace.service.CombatMaidBookService;
import com.github.tlmbeyondspace.service.DistressRecallService;
import com.github.tlmbeyondspace.service.MaidRosterService;
import com.github.tlmbeyondspace.service.OwnerFollowTeleportService;
import com.github.tlmbeyondspace.service.RescueSessionManager;
import com.github.tlmbeyondspace.data.PendingBindingClearData;
import com.github.tlmbeyondspace.data.PendingProfileResetData;
import com.github.tlmbeyondspace.item.SpacetimeRescueCharmItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CommonEventHandler {
    public static final CommonEventHandler INSTANCE = new CommonEventHandler();

    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        var maid = event.getMaid();
        if (maid.level() instanceof ServerLevel level) {
            MaidRosterService.observe(maid);
            PendingBindingClearData.get(level).process(maid);
            PendingProfileResetData.get(level).process(maid);
            OwnerFollowTeleportService.tick(maid);
        }
        RescueSessionManager.INSTANCE.tick(maid);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                && !event.getLevel().isClientSide) {
            MaidRosterService.observe(maid);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        isolateSneakBookInteraction(event);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        isolateSneakBookInteraction(event);
    }

    private void isolateSneakBookInteraction(PlayerInteractEvent event) {
        if (event.getEntity().isShiftKeyDown()
                && event.getItemStack().getItem() instanceof SpacetimeRescueCharmItem
                && !(event instanceof PlayerInteractEvent.EntityInteract entityInteract
                && entityInteract.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)
                && !(event instanceof PlayerInteractEvent.EntityInteractSpecific specific
                && specific.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)) {
            if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
                CombatMaidBookService.resetAllToForbidden(player);
            }
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(
                    event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide && !event.isCanceled()) {
            DamageSignalService.record(victim, event.getSource());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DistressRecallService.recallForOwnerQuiet(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DistressRecallService.recallForOwnerQuiet(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (PromaidCompat.shouldHandoffOwnerDeath()) {
                DistressRecallService.releaseForOwnerDeath(player);
            } else {
                DistressRecallService.recallForOwnerQuiet(player);
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        PromaidCompat.applyStartupCompatibility();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        DamageSignalService.clearAll();
        MaidRosterService.clearCaches();
        RescueSessionManager.INSTANCE.clearCaches();
    }

    private CommonEventHandler() {
    }
}
