package com.github.tlmbeyondspace.registry;

import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.inventory.RescueCombatConfigMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            ForgeRegistries.MENU_TYPES, TlmBeyondSpace.MOD_ID);

    public static final RegistryObject<MenuType<RescueCombatConfigMenu>> RESCUE_COMBAT_CONFIG =
            MENUS.register("rescue_combat_config", () -> IForgeMenuType.create(RescueCombatConfigMenu::new));

    private ModMenus() {
    }
}
