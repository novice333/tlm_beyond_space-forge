package com.github.tlmbeyondspace.registry;

import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.item.DistressSignalItem;
import com.github.tlmbeyondspace.item.MaidWeaponCaseItem;
import com.github.tlmbeyondspace.item.SpacetimeRescueCharmItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TlmBeyondSpace.MOD_ID);

    public static final RegistryObject<Item> SPACETIME_RESCUE_CHARM = ITEMS.register("spacetime_rescue_charm",
            () -> new SpacetimeRescueCharmItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DISTRESS_SIGNAL = ITEMS.register("distress_signal",
            () -> new DistressSignalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<MaidWeaponCaseItem> MAID_WEAPON_CASE = ITEMS.register("maid_weapon_case",
            () -> new MaidWeaponCaseItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
