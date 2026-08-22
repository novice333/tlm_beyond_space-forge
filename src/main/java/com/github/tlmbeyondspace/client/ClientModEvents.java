package com.github.tlmbeyondspace.client;

import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.client.screen.RescueCombatConfigScreen;
import com.github.tlmbeyondspace.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TlmBeyondSpace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                ModMenus.RESCUE_COMBAT_CONFIG.get(), RescueCombatConfigScreen::new));
    }

    private ClientModEvents() {
    }
}
