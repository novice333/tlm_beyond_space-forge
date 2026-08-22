package com.github.tlmbeyondspace.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.client.screen.RescueCombatConfigScreen;
import com.github.tlmbeyondspace.client.widget.RescueCombatTabButton;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.network.packet.OpenRescueCombatConfigC2SPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TlmBeyondSpace.MOD_ID, value = Dist.CLIENT)
public final class ClientMaidConfigEvents {
    private static final String TAB_BUTTON_ID = TlmBeyondSpace.MOD_ID + ":rescue_combat_tab";

    @SubscribeEvent
    public static void onMaidGuiInit(MaidContainerGuiEvent.Init event) {
        if (event.getGui() instanceof RescueCombatConfigScreen) {
            return;
        }
        EntityMaid maid = event.getGui().getMenu().getMaid();
        if (maid == null) {
            return;
        }
        event.addButton(TAB_BUTTON_ID, new RescueCombatTabButton(
                event.getLeftPos(), event.getTopPos(), true,
                pressed -> BeyondSpaceNetwork.CHANNEL.sendToServer(
                        new OpenRescueCombatConfigC2SPacket(maid.getId()))));
    }

    private ClientMaidConfigEvents() {
    }
}
