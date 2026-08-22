package com.github.tlmbeyondspace;

import com.mojang.logging.LogUtils;
import com.github.tlmbeyondspace.config.BeyondSpaceClientConfig;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import com.github.tlmbeyondspace.event.CommonEventHandler;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.registry.ModItems;
import com.github.tlmbeyondspace.registry.ModMenus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

@Mod(TlmBeyondSpace.MOD_ID)
public final class TlmBeyondSpace {
    public static final String MOD_ID = "tlm_beyond_space";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TlmBeyondSpace() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(this::addCreativeTabItems);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BeyondSpaceCommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BeyondSpaceClientConfig.SPEC);
        BeyondSpaceNetwork.register();
        MinecraftForge.EVENT_BUS.register(CommonEventHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TLM Beyond Space loaded");
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SPACETIME_RESCUE_CHARM);
            event.accept(ModItems.DISTRESS_SIGNAL);
            event.accept(ModItems.MAID_WEAPON_CASE);
        }
    }
}
