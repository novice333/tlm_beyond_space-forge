package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.data.CombatMaidBookData;
import com.github.tlmbeyondspace.data.MaidCombatPreferenceData;
import com.github.tlmbeyondspace.data.MaidRescueProfileData;
import com.github.tlmbeyondspace.data.PendingBindingClearData;
import com.github.tlmbeyondspace.data.PendingProfileResetData;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CombatMaidBookService {
    public static void bind(EntityMaid maid, ServerPlayer player, ItemStack book) {
        UUID bookId = CombatMaidBookData.ensureBookId(book);
        TaskModeProfile profile = TaskModeProfile.fromItem(book);
        MaidRescueProfileData.Data existing = MaidRescueProfileData.get(maid);
        if (existing.bound() && existing.profile() != null) {
            MaidCombatPreferenceData.migrateIfUnset(maid, existing.profile().getCombatTask());
        }
        MaidCombatPreferenceData.migrateIfUnset(maid, profile.getCombatTask());
        MaidRescueProfileData.bind(maid, player.getUUID(), profile, bookId);
        CombatMaidBookData.addBinding(book, maid.getUUID());
    }

    public static void clearSingle(EntityMaid maid, ServerPlayer player, ItemStack book) {
        UUID bookId = CombatMaidBookData.getBookId(book);
        MaidRescueProfileData.Data binding = MaidRescueProfileData.get(maid);
        boolean sameBook = bookId == null
                ? binding.sourceBookIdOptional().isEmpty()
                : binding.sourceBookIdOptional().filter(bookId::equals).isPresent();
        if (binding.bound() && binding.binderIdOptional().filter(player.getUUID()::equals).isPresent() && sameBook) {
            RescueSessionManager.INSTANCE.clearBindingSafely(maid);
        }
        CombatMaidBookData.removeBinding(book, maid.getUUID());
    }

    public static void clearAll(ServerPlayer player, ItemStack book) {
        UUID bookId = CombatMaidBookData.getBookId(book);
        boolean legacy = bookId == null;
        Set<UUID> maidIds = new LinkedHashSet<>(CombatMaidBookData.getBindings(book));
        if (legacy) {
            maidIds.addAll(MaidRosterService.knownOwnedMaidIds(player));
        }

        int immediate = 0;
        int pending = 0;
        for (UUID maidId : maidIds) {
            var loaded = MaidRosterService.findLoadedMaid(player.server, maidId);
            if (loaded.isPresent()) {
                EntityMaid maid = loaded.get();
                if (maid.isOwnedBy(player) && matchesBinding(maid, player.getUUID(), bookId, legacy)) {
                    RescueSessionManager.INSTANCE.clearBindingSafely(maid);
                    immediate++;
                }
            } else if (player.level() instanceof ServerLevel level) {
                PendingBindingClearData.get(level).add(maidId, player.getUUID(), bookId, legacy);
                pending++;
            }
        }
        CombatMaidBookData.clearBindings(book);
        player.displayClientMessage(Component.translatable("message.tlm_beyond_space.all_bindings_cleared",
                immediate, pending), false);
    }

    public static void resetAllToForbidden(ServerPlayer player) {
        Set<UUID> maidIds = MaidRosterService.knownOwnedMaidIds(player);
        int immediate = 0;
        int pending = 0;
        for (UUID maidId : maidIds) {
            var loaded = MaidRosterService.findLoadedMaid(player.server, maidId);
            if (loaded.isPresent() && loaded.get().isOwnedBy(player)) {
                MaidRescueProfileData.updateProfile(loaded.get(), player.getUUID(), new TaskModeProfile());
                immediate++;
            } else if (player.level() instanceof ServerLevel level) {
                PendingProfileResetData.get(level).add(maidId, player.getUUID());
                pending++;
            }
        }
        player.displayClientMessage(Component.translatable(
                "message.tlm_beyond_space.all_profiles_forbidden", immediate, pending), false);
    }

    private static boolean matchesBinding(EntityMaid maid, UUID ownerId, UUID bookId, boolean legacy) {
        MaidRescueProfileData.Data binding = MaidRescueProfileData.get(maid);
        if (!binding.bound() || binding.binderIdOptional().filter(ownerId::equals).isEmpty()) {
            return false;
        }
        return legacy ? binding.sourceBookIdOptional().isEmpty()
                : binding.sourceBookIdOptional().filter(bookId::equals).isPresent();
    }

    private CombatMaidBookService() {
    }
}
