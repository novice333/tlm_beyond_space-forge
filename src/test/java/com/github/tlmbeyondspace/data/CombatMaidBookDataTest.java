package com.github.tlmbeyondspace.data;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatMaidBookDataTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bookIdentityAndBindingsSurviveStackCopy() {
        ItemStack book = new ItemStack(Items.BOOK);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID bookId = CombatMaidBookData.ensureBookId(book);
        CombatMaidBookData.addBinding(book, first);
        CombatMaidBookData.addBinding(book, first);
        CombatMaidBookData.addBinding(book, second);

        ItemStack copied = book.copy();
        assertNotNull(bookId);
        assertEquals(bookId, CombatMaidBookData.getBookId(copied));
        assertEquals(2, CombatMaidBookData.getBindings(copied).size());

        CombatMaidBookData.clearBindings(copied);
        assertTrue(CombatMaidBookData.getBindings(copied).isEmpty());
        assertEquals(bookId, CombatMaidBookData.getBookId(copied));
    }
}
