package com.github.tlmbeyondspace.network.packet;

import com.github.tlmbeyondspace.data.DistressSignalData;
import com.github.tlmbeyondspace.item.DistressSignalItem;
import com.github.tlmbeyondspace.service.MaidRosterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public record SaveDistressRosterC2SPacket(InteractionHand hand, List<Entry> entries,
                                          boolean recallMode) {
    public SaveDistressRosterC2SPacket(InteractionHand hand, List<Entry> entries) {
        this(hand, entries, false);
    }

    public static void encode(SaveDistressRosterC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeUUID(entry.maidId());
            buffer.writeBoolean(entry.enabled());
            buffer.writeResourceLocation(entry.combatTask());
        }
        buffer.writeBoolean(packet.recallMode);
    }

    public static SaveDistressRosterC2SPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        int announced = buffer.readVarInt();
        if (announced < 0 || announced > DistressSignalData.MAX_SELECTIONS) {
            throw new IllegalArgumentException("Invalid distress roster size: " + announced);
        }
        List<Entry> entries = new ArrayList<>(announced);
        for (int i = 0; i < announced; i++) {
            UUID maidId = buffer.readUUID();
            boolean enabled = buffer.readBoolean();
            ResourceLocation task = buffer.readResourceLocation();
            entries.add(new Entry(maidId, enabled, task));
        }
        return new SaveDistressRosterC2SPacket(hand, entries, buffer.readBoolean());
    }

    public static void handle(SaveDistressRosterC2SPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = contextSupplier.get().getSender();
        if (player == null) return;
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof DistressSignalItem)) return;
        DistressSignalData oldData = DistressSignalData.fromItem(stack);
        if (!oldData.canUse(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.signal_wrong_owner"), true);
            return;
        }

        Set<UUID> ownedMaids = new HashSet<>(MaidRosterService.knownOwnedMaidIds(player));
        // Preserve entries already stored by older versions even if their entity and TLM record are currently absent.
        ownedMaids.addAll(oldData.selections().keySet());
        Map<UUID, DistressSignalData.Selection> selections = new LinkedHashMap<>();
        for (Entry entry : packet.entries) {
            if (!ownedMaids.contains(entry.maidId())) continue;
            selections.put(entry.maidId(), new DistressSignalData.Selection(entry.enabled(), entry.combatTask()));
        }
        DistressSignalData.writeItem(stack, new DistressSignalData(player.getUUID(), selections, packet.recallMode));
        player.displayClientMessage(Component.translatable("message.tlm_beyond_space.signal_roster_saved",
                selections.values().stream().filter(DistressSignalData.Selection::enabled).count()), true);
    }

    public record Entry(UUID maidId, boolean enabled, ResourceLocation combatTask) {
    }
}
