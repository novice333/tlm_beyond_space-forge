package com.github.tlmbeyondspace.network;

import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.network.packet.SaveCharmProfileC2SPacket;
import com.github.tlmbeyondspace.network.packet.OpenDistressRosterS2CPacket;
import com.github.tlmbeyondspace.network.packet.SaveDistressRosterC2SPacket;
import com.github.tlmbeyondspace.network.packet.SaveMaidCombatPreferenceC2SPacket;
import com.github.tlmbeyondspace.network.packet.SaveMaidRescueProfileC2SPacket;
import com.github.tlmbeyondspace.network.packet.OpenRescueCombatConfigC2SPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BeyondSpaceNetwork {
    private static final String PROTOCOL = "9";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(TlmBeyondSpace.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SaveCharmProfileC2SPacket.class, id++)
                .encoder(SaveCharmProfileC2SPacket::encode)
                .decoder(SaveCharmProfileC2SPacket::decode)
                .consumerMainThread(SaveCharmProfileC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenDistressRosterS2CPacket.class, id++)
                .encoder(OpenDistressRosterS2CPacket::encode)
                .decoder(OpenDistressRosterS2CPacket::decode)
                .consumerMainThread(OpenDistressRosterS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveDistressRosterC2SPacket.class, id++)
                .encoder(SaveDistressRosterC2SPacket::encode)
                .decoder(SaveDistressRosterC2SPacket::decode)
                .consumerMainThread(SaveDistressRosterC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveMaidCombatPreferenceC2SPacket.class, id++)
                .encoder(SaveMaidCombatPreferenceC2SPacket::encode)
                .decoder(SaveMaidCombatPreferenceC2SPacket::decode)
                .consumerMainThread(SaveMaidCombatPreferenceC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenRescueCombatConfigC2SPacket.class, id++)
                .encoder(OpenRescueCombatConfigC2SPacket::encode)
                .decoder(OpenRescueCombatConfigC2SPacket::decode)
                .consumerMainThread(OpenRescueCombatConfigC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveMaidRescueProfileC2SPacket.class, id++)
                .encoder(SaveMaidRescueProfileC2SPacket::encode)
                .decoder(SaveMaidRescueProfileC2SPacket::decode)
                .consumerMainThread(SaveMaidRescueProfileC2SPacket::handle)
                .add();
    }

    private BeyondSpaceNetwork() {
    }
}
