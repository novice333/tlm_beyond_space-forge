package com.github.tlmbeyondspace.inventory;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tlmbeyondspace.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class RescueCombatConfigMenu extends AbstractMaidContainer {
    public RescueCombatConfigMenu(int windowId, Inventory inventory, FriendlyByteBuf buffer) {
        this(windowId, inventory, buffer.readInt());
    }

    public RescueCombatConfigMenu(int windowId, Inventory inventory, int maidId) {
        super(ModMenus.RESCUE_COMBAT_CONFIG.get(), windowId, inventory, maidId);
    }

    public static MenuProvider create(int maidId) {
        return new SimpleMenuProvider(
                (windowId, inventory, player) -> new RescueCombatConfigMenu(windowId, inventory, maidId),
                Component.translatable("screen.tlm_beyond_space.rescue_combat_config"));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
