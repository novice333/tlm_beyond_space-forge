package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CombatMaidBookData {
    private static final String ROOT_TAG = "BeyondSpaceBookBindings";
    private static final int MAX_BINDINGS = 512;

    public static UUID ensureBookId(ItemStack book) {
        CompoundTag data = getOrCreateData(book);
        if (!data.hasUUID("BookId")) {
            data.putUUID("BookId", UUID.randomUUID());
        }
        return data.getUUID("BookId");
    }

    public static UUID getBookId(ItemStack book) {
        CompoundTag root = book.getTag();
        if (root == null || !root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag data = root.getCompound(ROOT_TAG);
        return data.hasUUID("BookId") ? data.getUUID("BookId") : null;
    }

    public static Set<UUID> getBindings(ItemStack book) {
        Set<UUID> bindings = new LinkedHashSet<>();
        CompoundTag root = book.getTag();
        if (root == null || !root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return bindings;
        }
        ListTag entries = root.getCompound(ROOT_TAG).getList("Maids", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size() && bindings.size() < MAX_BINDINGS; i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Maid")) {
                bindings.add(entry.getUUID("Maid"));
            }
        }
        return bindings;
    }

    public static void addBinding(ItemStack book, UUID maidId) {
        Set<UUID> bindings = getBindings(book);
        if (bindings.size() < MAX_BINDINGS) {
            bindings.add(maidId);
        }
        writeBindings(book, bindings);
    }

    public static void removeBinding(ItemStack book, UUID maidId) {
        Set<UUID> bindings = getBindings(book);
        if (bindings.remove(maidId)) {
            writeBindings(book, bindings);
        }
    }

    public static void clearBindings(ItemStack book) {
        writeBindings(book, Set.of());
    }

    private static void writeBindings(ItemStack book, Set<UUID> bindings) {
        CompoundTag data = getOrCreateData(book);
        ListTag entries = new ListTag();
        bindings.stream().limit(MAX_BINDINGS).forEach(maidId -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Maid", maidId);
            entries.add(entry);
        });
        data.put("Maids", entries);
    }

    private static CompoundTag getOrCreateData(ItemStack book) {
        CompoundTag root = book.getOrCreateTag();
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            root.put(ROOT_TAG, new CompoundTag());
        }
        return root.getCompound(ROOT_TAG);
    }

    private CombatMaidBookData() {
    }
}
